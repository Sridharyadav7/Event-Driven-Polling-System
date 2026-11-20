package com.polling.event;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event published to Kafka when a vote is cast.
 * Contains a unique eventId for idempotent processing by VoteProcessor.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteCastEvent {

    /** Unique ID for this event — used by VoteProcessor to deduplicate Kafka redeliveries. */
    private UUID eventId;

    private UUID pollId;
    private UUID voterId;
    private String optionId;
    private Instant timestamp;
}
