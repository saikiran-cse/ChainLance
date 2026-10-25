package com.chainlance.dto;

import com.chainlance.entity.DisputeResponse;
import java.time.Instant;

public record DisputeReplyResponse(
        Long id,
        Long disputeId,
        PublicUserResponse user,
        String response,
        String evidenceLinks,
        Instant createdAt
) {
    public static DisputeReplyResponse from(DisputeResponse r) {
        return new DisputeReplyResponse(
                r.getId(),
                r.getDispute().getId(),
                r.getUser() != null ? PublicUserResponse.from(r.getUser()) : null,
                r.getResponse(),
                r.getEvidenceLinks(),
                r.getCreatedAt()
        );
    }
}
