package com.chainlance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OpenDisputeRequest(
        @NotBlank @Size(max = 5000) String reason,
        String txHash
) {}
