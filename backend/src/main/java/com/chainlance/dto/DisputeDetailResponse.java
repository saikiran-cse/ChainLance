package com.chainlance.dto;

import com.chainlance.entity.Dispute;
import com.chainlance.entity.enums.DisputeDecision;
import com.chainlance.entity.enums.DisputeStatus;
import java.time.Instant;
import java.util.List;

public record DisputeDetailResponse(
        Long id,
        Long milestoneId,
        PublicUserResponse openedBy,
        String reason,
        String reasonHash,
        DisputeStatus status,
        DisputeDecision adminDecision,
        String adminFreelancerWei,
        String adminClientWei,
        String adminExplanation,
        String resolutionHash,
        PublicUserResponse decidedBy,
        Instant decidedAt,
        String openTxHash,
        String resolvedTxHash,
        Instant createdAt,
        Instant updatedAt,
        List<DisputeReplyResponse> responses
) {
    public static DisputeDetailResponse from(Dispute d, List<DisputeReplyResponse> replies) {
        return new DisputeDetailResponse(
                d.getId(),
                d.getMilestone().getId(),
                d.getOpenedBy() != null ? PublicUserResponse.from(d.getOpenedBy()) : null,
                d.getReason(),
                d.getReasonHash(),
                d.getStatus(),
                d.getAdminDecision(),
                d.getAdminFreelancerWei() != null ? d.getAdminFreelancerWei().toPlainString() : null,
                d.getAdminClientWei() != null ? d.getAdminClientWei().toPlainString() : null,
                d.getAdminExplanation(),
                d.getResolutionHash(),
                d.getDecidedBy() != null ? PublicUserResponse.from(d.getDecidedBy()) : null,
                d.getDecidedAt(),
                d.getOpenTx() != null ? d.getOpenTx().getTxHash() : null,
                d.getResolvedTx() != null ? d.getResolvedTx().getTxHash() : null,
                d.getCreatedAt(),
                d.getUpdatedAt(),
                replies != null ? replies : List.of()
        );
    }
}
