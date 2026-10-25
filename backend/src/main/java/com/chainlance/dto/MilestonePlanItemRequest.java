package com.chainlance.dto;

import java.math.BigDecimal;

public record MilestonePlanItemRequest(
        String title,
        String description,
        BigDecimal percentage,
        BigDecimal inr,
        Integer durationDays
) {
}
