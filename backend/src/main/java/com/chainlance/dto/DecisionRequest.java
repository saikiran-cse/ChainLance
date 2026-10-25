package com.chainlance.dto;

import jakarta.validation.constraints.Size;

public record DecisionRequest(
        @Size(max = 2000) String note
) {}
