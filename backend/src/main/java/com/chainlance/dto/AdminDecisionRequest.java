package com.chainlance.dto;

import com.chainlance.entity.enums.DisputeDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminDecisionRequest(
        @NotNull DisputeDecision decision,
        String freelancerWei,
        String clientWei,
        @NotBlank @Size(max = 5000) String explanation
) {}
