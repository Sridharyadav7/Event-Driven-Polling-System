package com.polling.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Represents a poll with a question, multiple options, and a lifecycle.
 * Options are stored as JSONB for schema flexibility.
 */
@Entity
@Table(name = "polls", indexes = {
    @Index(name = "idx_polls_end_time", columnList = "end_time"),
    @Index(name = "idx_polls_creator_id", columnList = "creator_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Poll {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "poll_id", updatable = false, nullable = false)
    private UUID pollId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(name = "question", nullable = false, length = 500)
    private String question;

    /**
     * Poll options stored as JSONB array: [{"optionId":"uuid","text":"..."}]
     * Using JSONB avoids a separate options table while supporting indexing.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options", nullable = false, columnDefinition = "jsonb")
    private List<PollOption> options;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public boolean isActive() {
        return status == Status.ACTIVE && Instant.now().isBefore(endTime);
    }

    public enum Status {
        ACTIVE, CLOSED
    }

    /** Embedded value object representing a single poll option. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PollOption {
        private String optionId;
        private String text;
    }
}
