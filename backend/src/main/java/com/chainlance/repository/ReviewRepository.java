package com.chainlance.repository;

import com.chainlance.entity.Review;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByAgreementIdAndReviewerId(Long agreementId, Long reviewerId);

    @EntityGraph(attributePaths = {"reviewer"})
    List<Review> findByRevieweeIdOrderByCreatedAtDesc(Long revieweeId);

    long countByRevieweeId(Long revieweeId);

    /** Null when the user has no reviews. */
    @Query("select avg(r.rating) from Review r where r.reviewee.id = :userId")
    Double findAverageRatingForReviewee(@Param("userId") Long userId);
}
