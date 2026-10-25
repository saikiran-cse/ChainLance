package com.chainlance.controller;

import com.chainlance.dto.CreateReviewRequest;
import com.chainlance.dto.ReviewResponse;
import com.chainlance.dto.UserReviewsResponse;
import com.chainlance.security.AppUserDetails;
import com.chainlance.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/agreements/{id}/reviews")
    public ReviewResponse createReview(@AuthenticationPrincipal AppUserDetails current,
                                       @PathVariable Long id,
                                       @Valid @RequestBody CreateReviewRequest request) {
        return reviewService.createReview(id, current.getId(), request);
    }

    @GetMapping("/users/{id}/reviews")
    public UserReviewsResponse getUserReviews(@PathVariable Long id) {
        return reviewService.getUserReviews(id);
    }
}
