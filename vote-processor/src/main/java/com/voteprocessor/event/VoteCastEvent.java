package com.voteprocessor.event;

import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Deserialized from Kafka — must match the structure published by poll-service.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoteCastEvent {
    private UUID eventId;
    private UUID pollId;
    private UUID voterId;
    private String optionId;
    private Instant timestamp;
}
