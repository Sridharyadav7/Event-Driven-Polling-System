package com.polling.service;

import com.polling.config.KafkaConfig;
import com.polling.dto.CreatePollRequest;
import com.polling.dto.PollResponse;
import com.polling.dto.VoteRequest;
import com.polling.entity.Poll;
import com.polling.entity.User;
import com.polling.event.VoteCastEvent;
import com.polling.exception.*;
import com.polling.repository.PollRepository;
import com.polling.repository.UserRepository;
import com.polling.repository.VoteRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core business logic for poll management and vote submission.
 *
 * Vote flow:
 * 1. Validate poll is active and option is valid.
 * 2. Check for duplicate vote (early exit with 409).
 * 3. Publish VoteCastEvent to Kafka (idempotent producer).
 * 4. Return 200 immediately — the VoteProcessor handles DB/Redis writes asynchronously.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PollService {

    private final PollRepository pollRepository;
    private final UserRepository userRepository;
    private final VoteRepository voteRepository;
    private final KafkaTemplate<String, VoteCastEvent> kafkaTemplate;
    private final ResultsService resultsService;
    private final MeterRegistry meterRegistry;

    @Transactional
    public PollResponse createPoll(UUID creatorId, CreatePollRequest request) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("User not found: " + creatorId));

        List<Poll.PollOption> options = request.getOptions().stream()
                .map(text -> Poll.PollOption.builder()
                        .optionId(UUID.randomUUID().toString())
                        .text(text)
                        .build())
                .collect(Collectors.toList());

        Poll poll = Poll.builder()
                .creator(creator)
                .question(request.getQuestion())
                .options(options)
                .startTime(Instant.now())
                .endTime(request.getEndTime())
                .status(Poll.Status.ACTIVE)
                .build();

        poll = pollRepository.save(poll);
        log.info("Poll created: {} by user: {}", poll.getPollId(), creatorId);

        // Track custom metric
        Counter.builder("polls.created.total")
                .description("Total polls created")
                .register(meterRegistry)
                .increment();

        return toResponse(poll);
    }

    @Transactional(readOnly = true)
    public Page<PollResponse> listPolls(Pageable pageable) {
        return pollRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PollResponse getPoll(UUID pollId) {
        Poll poll = findActivePollOrThrow(pollId);
        PollResponse response = toResponse(poll);
        response.setVoteCounts(resultsService.getVoteCounts(pollId));
        return response;
    }

    /**
     * Cast a vote:
     * - Validates poll is active and optionId is valid.
     * - Rejects duplicate vote early (before Kafka) with HTTP 409.
     * - Publishes VoteCastEvent to Kafka and returns immediately.
     * The VoteProcessor handles the actual DB write and Redis increment.
     */
    @Transactional
    public void castVote(UUID pollId, UUID voterId, VoteRequest request) {
        Poll poll = pollRepository.findById(pollId)
                .orElseThrow(() -> new PollNotFoundException("Poll not found: " + pollId));

        if (!poll.isActive()) {
            throw new PollClosedException("Poll " + pollId + " is closed or expired");
        }

        // Validate optionId belongs to this poll
        boolean validOption = poll.getOptions().stream()
                .anyMatch(o -> o.getOptionId().equals(request.getOptionId()));
        if (!validOption) {
            throw new InvalidOptionException("Option '" + request.getOptionId() + "' is not valid for this poll");
        }

        // Reject duplicate vote early — avoids publishing unnecessary Kafka events
        if (voteRepository.existsByPollIdAndVoterId(pollId, voterId)) {
            throw new DuplicateVoteException("User has already voted on poll " + pollId);
        }

        VoteCastEvent event = VoteCastEvent.builder()
                .eventId(UUID.randomUUID())
                .pollId(pollId)
                .voterId(voterId)
                .optionId(request.getOptionId())
                .timestamp(Instant.now())
                .build();

        // Use pollId as partition key so all votes for a poll go to the same partition (ordering)
        kafkaTemplate.send(KafkaConfig.VOTE_CAST_TOPIC, pollId.toString(), event);
        log.info("Published VoteCastEvent {} for poll {} by voter {}", event.getEventId(), pollId, voterId);

        Counter.builder("votes.submitted.total")
                .description("Total vote events published to Kafka")
                .register(meterRegistry)
                .increment();
    }

    /** Scheduled job: closes polls whose end time has passed. Runs every minute. */
    @Scheduled(fixedDelayString = "${app.poll.close-check-interval-ms:60000}")
    @Transactional
    public void closeExpiredPolls() {
        int closed = pollRepository.closeExpiredPolls(Instant.now());
        if (closed > 0) {
            log.info("Closed {} expired polls", closed);
        }
    }

    // ---- Mapper ----

    private PollResponse toResponse(Poll poll) {
        PollResponse response = new PollResponse();
        response.setPollId(poll.getPollId());
        response.setQuestion(poll.getQuestion());
        response.setStartTime(poll.getStartTime());
        response.setEndTime(poll.getEndTime());
        response.setStatus(poll.getStatus());
        response.setCreatedAt(poll.getCreatedAt());
        response.setCreatorId(poll.getCreator().getId());
        response.setCreatorEmail(poll.getCreator().getEmail());

        List<PollResponse.OptionDto> optionDtos = poll.getOptions().stream()
                .map(o -> {
                    PollResponse.OptionDto dto = new PollResponse.OptionDto();
                    dto.setOptionId(o.getOptionId());
                    dto.setText(o.getText());
                    return dto;
                }).collect(Collectors.toList());
        response.setOptions(optionDtos);
        return response;
    }

    private Poll findActivePollOrThrow(UUID pollId) {
        return pollRepository.findById(pollId)
                .orElseThrow(() -> new PollNotFoundException("Poll not found: " + pollId));
    }
}
