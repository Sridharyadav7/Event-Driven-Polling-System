package com.voteprocessor.service;

import com.voteprocessor.entity.ProcessedEvent;
import com.voteprocessor.event.VoteCastEvent;
import com.voteprocessor.repository.ProcessedEventRepository;
import com.voteprocessor.repository.VoteRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Core idempotent vote processing logic:
 *
 * 1. Check processed_events table — skip if eventId already seen.
 * 2. INSERT ... ON CONFLICT DO NOTHING (handles concurrent duplicates at DB level).
 * 3. Increment Redis live counter via RedisCounterService (circuit-breaker protected).
 * 4. Mark event as processed in DB.
 * 5. Commit transaction.
 *
 * This provides exactly-once semantics at the application level even with
 * Kafka at-least-once delivery guarantees.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VoteProcessingService {

    private final VoteRepository voteRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final RedisCounterService redisCounterService;
    private final MeterRegistry meterRegistry;

    @Transactional
    public void processVote(VoteCastEvent event) {
        log.info("Processing VoteCastEvent {} for poll {} voter {}",
                event.getEventId(), event.getPollId(), event.getVoterId());

        // Step 1: Idempotency check using processed_events table
        if (processedEventRepository.existsByEventId(event.getEventId())) {
            log.info("Duplicate event {} — already processed, skipping", event.getEventId());
            incrementCounter("votes.skipped.duplicate.total");
            return;
        }

        // Step 2: Insert vote with ON CONFLICT DO NOTHING
        // Returns 1 if inserted, 0 if unique constraint (poll_id, voter_id) prevented it
        try {
            int inserted = voteRepository.insertIgnoreDuplicate(
                    UUID.randomUUID(),
                    event.getPollId(),
                    event.getVoterId(),
                    event.getOptionId()
            );

            if (inserted > 0) {
                // Step 3: Increment Redis counter for live tally (circuit breaker protected)
                redisCounterService.incrementCounter(event.getPollId(), event.getOptionId());
                incrementCounter("votes.processed.total");
                log.info("Vote inserted for poll {} voter {}", event.getPollId(), event.getVoterId());
            } else {
                // Vote already in DB (duplicate from a different event path)
                log.info("Vote already exists in DB for poll {} voter {} — no DB change",
                        event.getPollId(), event.getVoterId());
                incrementCounter("votes.skipped.duplicate.total");
            }
        } catch (DataIntegrityViolationException e) {
            // Race condition between concurrent consumers in the same group
            log.warn("Concurrent duplicate vote for poll {} voter {}: {}",
                    event.getPollId(), event.getVoterId(), e.getMessage());
            incrementCounter("votes.skipped.duplicate.total");
        }

        // Step 4: Mark this event as processed — prevents reprocessing on Kafka redeliver
        processedEventRepository.save(ProcessedEvent.builder()
                .eventId(event.getEventId())
                .processedAt(Instant.now())
                .build());

        log.info("Event {} marked as processed", event.getEventId());
    }

    private void incrementCounter(String name) {
        Counter.builder(name).register(meterRegistry).increment();
    }
}
