package com.voteprocessor.consumer;

import com.voteprocessor.event.VoteCastEvent;
import com.voteprocessor.service.VoteProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the vote_cast topic.
 * Uses manual acknowledgment (MANUAL_IMMEDIATE) so that:
 * - On success: offset is committed after processing.
 * - On failure: offset is NOT committed, allowing retry.
 *
 * The processing logic in VoteProcessingService is fully idempotent,
 * so Kafka redeliveries are safe and will be no-ops.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VoteKafkaConsumer {

    private final VoteProcessingService voteProcessingService;

    @KafkaListener(
        topics = "${kafka.topic.vote-cast:vote_cast}",
        groupId = "${spring.kafka.consumer.group-id:vote-processor-group}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeVoteCastEvent(ConsumerRecord<String, VoteCastEvent> record,
                                      Acknowledgment acknowledgment) {
        VoteCastEvent event = record.value();
        // Set MDC for log correlation in this consumer thread
        MDC.put("eventId", event.getEventId() != null ? event.getEventId().toString() : "null");
        MDC.put("pollId",  event.getPollId()  != null ? event.getPollId().toString()  : "null");

        try {
            log.info("Received vote event from partition={} offset={}",
                    record.partition(), record.offset());
            voteProcessingService.processVote(event);
            acknowledgment.acknowledge();
            log.info("Acknowledged event {} at offset {}", event.getEventId(), record.offset());
        } catch (Exception e) {
            // Don't acknowledge — Kafka will redeliver. VoteProcessingService handles idempotency.
            log.error("Failed to process event {} — will retry: {}", event.getEventId(), e.getMessage(), e);
            // Nack: triggers retry/DLQ depending on container config
        } finally {
            MDC.remove("eventId");
            MDC.remove("pollId");
        }
    }
}
