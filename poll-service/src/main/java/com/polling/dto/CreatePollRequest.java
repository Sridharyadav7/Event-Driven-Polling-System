package com.polling.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
public class CreatePollRequest {

    @NotBlank(message = "Question is required")
    @Size(max = 500, message = "Question must not exceed 500 characters")
    private String question;

    @NotNull(message = "Options are required")
    @Size(min = 2, max = 10, message = "A poll must have between 2 and 10 options")
    private List<@NotBlank String> options;

    @NotNull(message = "End time is required")
    @Future(message = "End time must be in the future")
    private Instant endTime;
}
