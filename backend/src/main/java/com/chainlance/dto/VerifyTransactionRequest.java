package com.chainlance.dto;

import com.chainlance.entity.enums.TransactionType;
import jakarta.validation.constraints.NotBlank;

public record VerifyTransactionRequest(
        @NotBlank(message = "Transaction hash is required")
        String txHash,
        TransactionType type,
        Long agreementId,
        Long milestoneId
) {
}
