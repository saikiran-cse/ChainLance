package com.chainlance.dto;

import jakarta.validation.constraints.NotBlank;

public record AcceptSettlementRequest(
        @NotBlank String txHash
) {}
