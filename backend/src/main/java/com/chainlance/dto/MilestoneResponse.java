package com.chainlance.dto;

import com.chainlance.entity.Milestone;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.entity.enums.MilestoneWorkStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record MilestoneResponse(
        Long id,
        Long agreementId,
        int position,
        String description,
        String acceptanceCriteria,
        String amountWei,
        BigDecimal amountInr,
        Instant originalDeadline,
        Instant effectiveDeadline,
        MilestoneWorkStatus workStatus,
        MilestoneChainStatus chainStatus,
        String freelancerPaidWei,
        String clientRefundedWei,
        Instant createdAt,
        Instant updatedAt
) {
    private static final BigDecimal WEI_PER_ETH = new BigDecimal("1000000000000000000");

    public static MilestoneResponse from(Milestone m, BigDecimal inrPerEth) {
        BigDecimal amountInr = null;
        if (inrPerEth != null && inrPerEth.signum() > 0 && m.getAmountWei() != null) {
            amountInr = m.getAmountWei().multiply(inrPerEth).divide(WEI_PER_ETH, 2, RoundingMode.HALF_UP);
        }
        return new MilestoneResponse(
                m.getId(),
                m.getAgreement() != null ? m.getAgreement().getId() : null,
                m.getPosition(),
                m.getDescription(),
                m.getAcceptanceCriteria(),
                m.getAmountWei() != null ? m.getAmountWei().toPlainString() : "0",
                amountInr,
                m.getOriginalDeadline(),
                m.getEffectiveDeadline(),
                m.getWorkStatus(),
                m.getChainStatus(),
                m.getFreelancerPaidWei() != null ? m.getFreelancerPaidWei().toPlainString() : "0",
                m.getClientRefundedWei() != null ? m.getClientRefundedWei().toPlainString() : "0",
                m.getCreatedAt(),
                m.getUpdatedAt()
        );
    }
}
