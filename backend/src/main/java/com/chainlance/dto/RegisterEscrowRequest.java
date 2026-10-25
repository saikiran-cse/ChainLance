package com.chainlance.dto;

import jakarta.validation.constraints.NotBlank;

public record RegisterEscrowRequest(
        @NotBlank(message = "Transaction hash is required")
        String txHash
) {
}
