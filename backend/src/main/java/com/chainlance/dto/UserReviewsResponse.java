package com.chainlance.dto;

import java.util.List;

public record UserReviewsResponse(
        Long userId,
        long totalReviews,
        Double averageRating,
        List<ReviewResponse> reviews
) {}
