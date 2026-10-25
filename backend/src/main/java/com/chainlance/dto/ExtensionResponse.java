package com.chainlance.dto;

import com.chainlance.entity.DeadlineExtensionRequest;
import com.chainlance.entity.enums.ExtensionStatus;
import java.time.Instant;

public record ExtensionResponse(
        Long id,
        Long milestoneId,
        PublicUserResponse requestedBy,
        Instant currentDeadline,
        Instant requestedDeadline,
        String reason,
        ExtensionStatus status,
        PublicUserResponse decidedBy,
        String decisionNote,
        Instant requestedAt,
        Instant decidedAt
) {
    public static ExtensionResponse from(DeadlineExtensionRequest e) {
        return new ExtensionResponse(
                e.getId(),
                e.getMilestone().getId(),
                e.getRequestedBy() != null ? PublicUserResponse.from(e.getRequestedBy()) : null,
                e.getCurrentDeadline(),
                e.getRequestedDeadline(),
                e.getReason(),
                e.getStatus(),
                e.getDecidedBy() != null ? PublicUserResponse.from(e.getDecidedBy()) : null,
                e.getDecisionNote(),
                e.getRequestedAt(),
                e.getDecidedAt()
        );
    }
}
