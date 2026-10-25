package com.chainlance.dto;

import java.math.BigDecimal;

public record MilestonePlanItemResponse(
        Long id,
        Long projectId,
        int position,
        String description,
        BigDecimal allocationInr
) {
}
