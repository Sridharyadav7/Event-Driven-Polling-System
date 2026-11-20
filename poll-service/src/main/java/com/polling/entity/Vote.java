package com.polling.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Records a single vote cast by a user on a poll.
 * The UNIQUE constraint on (poll_id, voter_id) enforces one-vote-per-user-per-poll
 * at the database level — the strongest guarantee against duplicates.
 */
@Entity
@Table(name = "votes",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_votes_poll_voter", columnNames = {"poll_id", "voter_id"})
    },
    indexes = {
        @Index(name = "idx_votes_poll_id", columnList = "poll_id"),
        @Index(name = "idx_votes_poll_option", columnList = "poll_id, option_id")
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
