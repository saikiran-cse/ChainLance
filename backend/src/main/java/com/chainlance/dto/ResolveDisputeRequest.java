package com.chainlance.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveDisputeRequest(
        @NotBlank String txHash
) {}
