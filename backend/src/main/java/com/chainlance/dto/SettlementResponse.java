package com.chainlance.dto;

import com.chainlance.entity.SettlementProposal;
import com.chainlance.entity.enums.SettlementStatus;
import java.time.Instant;

public record SettlementResponse(
        Long id,
        Long milestoneId,
        PublicUserResponse proposer,
        String freelancerWei,
        String clientWei,
        String reason,
        SettlementStatus status,
        String proposeTxHash,
        String executedTxHash,
        Instant createdAt,
        Instant decidedAt
) {
    public static SettlementResponse from(SettlementProposal p) {
        return new SettlementResponse(
                p.getId(),
                p.getMilestone().getId(),
                p.getProposer() != null ? PublicUserResponse.from(p.getProposer()) : null,
                p.getFreelancerWei() != null ? p.getFreelancerWei().toPlainString() : "0",
                p.getClientWei() != null ? p.getClientWei().toPlainString() : "0",
                p.getReason(),
                p.getStatus(),
                p.getProposeTx() != null ? p.getProposeTx().getTxHash() : null,
                p.getExecutedTx() != null ? p.getExecutedTx().getTxHash() : null,
                p.getCreatedAt(),
                p.getDecidedAt()
        );
    }
}
