package com.polling.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VoteRequest {

    @NotBlank(message = "optionId is required")
    private String optionId;
}
