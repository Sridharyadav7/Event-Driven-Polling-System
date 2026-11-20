package com.voteprocessor.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotency table for Kafka consumer.
 * Before processing a VoteCastEvent, the consumer checks this table.
 * If the eventId is already present, the event is skipped (duplicate delivery).
 * This provides exactly-once semantics at the application level.
 */
@Entity
@Table(name = "processed_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
