package com.chainlance.dto;

import com.chainlance.entity.Review;
import java.time.Instant;

public record ReviewResponse(
        Long id,
        Long agreementId,
        PublicUserResponse reviewer,
        PublicUserResponse reviewee,
        byte rating,
        String comment,
        Instant createdAt
) {
    public static ReviewResponse from(Review r) {
        return new ReviewResponse(
                r.getId(),
                r.getAgreement().getId(),
                r.getReviewer() != null ? PublicUserResponse.from(r.getReviewer()) : null,
                r.getReviewee() != null ? PublicUserResponse.from(r.getReviewee()) : null,
                r.getRating(),
                r.getComment(),
                r.getCreatedAt()
        );
    }
}
