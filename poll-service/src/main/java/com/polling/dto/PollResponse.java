package com.polling.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.polling.entity.Poll;
import lombok.Data;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTO returned to clients for poll details.
 * Intentionally excludes JPA entity internals (no lazy proxies).
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PollResponse {

    private UUID pollId;
    private String question;
    private List<OptionDto> options;
    private Instant startTime;
    private Instant endTime;
    private Poll.Status status;
    private UUID creatorId;
    private String creatorEmail;
    private Instant createdAt;
    /** Live vote counts keyed by optionId — from Redis for active polls. */
    private Map<String, Long> voteCounts;

    @Data
    public static class OptionDto {
        private String optionId;
        private String text;
    }
}
