package com.voteprocessor.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Vote entity — mirrors the votes table managed by poll-service.
 * VoteProcessor inserts rows here using ON CONFLICT DO NOTHING semantics.
 */
@Entity
@Table(name = "votes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_votes_poll_voter", columnNames = {"poll_id", "voter_id"})
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "vote_id", updatable = false, nullable = false)
    private UUID voteId;

    @Column(name = "poll_id", nullable = false)
    private UUID pollId;

    @Column(name = "voter_id", nullable = false)
    private UUID voterId;

    @Column(name = "option_id", nullable = false, length = 100)
    private String optionId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
