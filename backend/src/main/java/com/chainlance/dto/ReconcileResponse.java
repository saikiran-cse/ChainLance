package com.chainlance.dto;

import com.chainlance.entity.enums.AgreementStatus;
import java.time.Instant;
import java.util.List;

public record ReconcileResponse(
        Long agreementId,
        AgreementStatus agreementStatus,
        List<MilestoneResponse> milestones,
        Instant reconciledAt
) {
}
