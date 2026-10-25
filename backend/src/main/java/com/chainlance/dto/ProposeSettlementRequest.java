package com.chainlance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProposeSettlementRequest(
        @NotBlank String freelancerWei,
        @NotBlank String clientWei,
        @Size(max = 2000) String reason,
        String txHash
) {}
