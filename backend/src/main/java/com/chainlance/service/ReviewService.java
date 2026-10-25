package com.chainlance.service;

import com.chainlance.dto.CreateReviewRequest;
import com.chainlance.dto.ReviewResponse;
import com.chainlance.dto.UserReviewsResponse;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.Review;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.AgreementStatus;
import com.chainlance.repository.AgreementRepository;
import com.chainlance.repository.ReviewRepository;
import com.chainlance.repository.UserRepository;
import com.chainlance.web.ApiException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final ReviewRepository reviews;
    private final AgreementRepository agreements;
    private final UserRepository users;
    private final AgreementWorkflowCoordinator coordinator;

    public ReviewService(ReviewRepository reviews, AgreementRepository agreements,
                         UserRepository users, AgreementWorkflowCoordinator coordinator) {
        this.reviews = reviews;
        this.agreements = agreements;
        this.users = users;
        this.coordinator = coordinator;
    }

    @Transactional
    public ReviewResponse createReview(Long agreementId, Long currentUserId, CreateReviewRequest request) {
        Agreement agreement = agreements.findById(agreementId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "AGREEMENT_NOT_FOUND", "Agreement not found"));
        var part = coordinator.checkAgreementParticipant(agreement, currentUserId);

        if (agreement.getStatus() != AgreementStatus.COMPLETED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AGREEMENT_NOT_COMPLETED",
                    "Reviews can only be submitted after the agreement is COMPLETED (current: " + agreement.getStatus() + ")");
        }

        if (reviews.existsByAgreementIdAndReviewerId(agreementId, currentUserId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "REVIEW_ALREADY_EXISTS",
                    "You have already submitted a review for this agreement");
        }

        User reviewer = users.findById(currentUserId).orElseThrow();
        User reviewee = part.isClient()
                ? agreement.getProposal().getFreelancer()
                : agreement.getProposal().getProject().getClient();

        if (reviewer.getId().equals(reviewee.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_REVIEW_SELF", "Reviewer and reviewee cannot be the same user");
        }

        Review review = new Review();
        review.setAgreement(agreement);
        review.setReviewer(reviewer);
        review.setReviewee(reviewee);
        review.setRating(request.rating());
        review.setComment(request.comment());

        return ReviewResponse.from(reviews.save(review));
    }

    @Transactional(readOnly = true)
    public UserReviewsResponse getUserReviews(Long targetUserId) {
        users.findById(targetUserId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found"));

        List<ReviewResponse> list = reviews.findByRevieweeIdOrderByCreatedAtDesc(targetUserId).stream()
                .map(ReviewResponse::from)
                .toList();
        long count = reviews.countByRevieweeId(targetUserId);
        Double avg = reviews.findAverageRatingForReviewee(targetUserId);

        return new UserReviewsResponse(targetUserId, count, avg, list);
    }
}
