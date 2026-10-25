package com.chainlance.dto;

import com.chainlance.entity.Submission;
import com.chainlance.entity.enums.SubmissionReviewStatus;
import java.time.Instant;

public record SubmissionResponse(
        Long id,
        Long milestoneId,
        String description,
        String links,
        Instant submittedAt,
        boolean late,
        SubmissionReviewStatus reviewStatus,
        String revisionReason,
        Instant reviewedAt
) {
    public static SubmissionResponse from(Submission s) {
        return new SubmissionResponse(
                s.getId(),
                s.getMilestone().getId(),
                s.getDescription(),
                s.getLinks(),
                s.getSubmittedAt(),
                s.isLate(),
                s.getReviewStatus(),
                s.getRevisionReason(),
                s.getReviewedAt()
        );
    }
}
