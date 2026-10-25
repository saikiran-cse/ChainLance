package com.chainlance.dto;

import com.chainlance.entity.Dispute;
import com.chainlance.entity.enums.DisputeDecision;
import com.chainlance.entity.enums.DisputeStatus;
import java.time.Instant;

public record DisputeSummaryResponse(
        Long id,
        Long milestoneId,
        Long agreementId,
        PublicUserResponse openedBy,
        String reason,
        DisputeStatus status,
        DisputeDecision adminDecision,
        String milestoneAmountWei,
        Instant createdAt
) {
    public static DisputeSummaryResponse from(Dispute d) {
        return new DisputeSummaryResponse(
                d.getId(),
                d.getMilestone().getId(),
                d.getMilestone().getAgreement().getId(),
                d.getOpenedBy() != null ? PublicUserResponse.from(d.getOpenedBy()) : null,
                d.getReason(),
                d.getStatus(),
                d.getAdminDecision(),
                d.getMilestone().getAmountWei() != null ? d.getMilestone().getAmountWei().toPlainString() : "0",
                d.getCreatedAt()
        );
    }
}
