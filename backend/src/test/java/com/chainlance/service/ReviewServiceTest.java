package com.chainlance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.chainlance.dto.CreateReviewRequest;
import com.chainlance.dto.ReviewResponse;
import com.chainlance.dto.UserReviewsResponse;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.Project;
import com.chainlance.entity.Proposal;
import com.chainlance.entity.Review;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.AgreementStatus;
import com.chainlance.repository.AgreementRepository;
import com.chainlance.repository.ReviewRepository;
import com.chainlance.repository.UserRepository;
import com.chainlance.web.ApiException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReviewServiceTest {

    private ReviewRepository reviews;
    private AgreementRepository agreements;
    private UserRepository users;
    private AgreementWorkflowCoordinator coordinator;
    private ReviewService service;

    private Agreement agreement;
    private User client;
    private User freelancer;

    @BeforeEach
    void setUp() {
        reviews = mock(ReviewRepository.class);
        agreements = mock(AgreementRepository.class);
        users = mock(UserRepository.class);
        coordinator = mock(AgreementWorkflowCoordinator.class);

        service = new ReviewService(reviews, agreements, users, coordinator);

        client = new User();
        ReflectionTestUtils.setField(client, "id", 1L);
        client.setDisplayName("Client");

        freelancer = new User();
        ReflectionTestUtils.setField(freelancer, "id", 2L);
        freelancer.setDisplayName("Freelancer");

        Project project = new Project();
        project.setClient(client);

        Proposal proposal = new Proposal();
        proposal.setProject(project);
        proposal.setFreelancer(freelancer);

        agreement = new Agreement();
        ReflectionTestUtils.setField(agreement, "id", 10L);
        agreement.setProposal(proposal);
        agreement.setStatus(AgreementStatus.COMPLETED);

        when(agreements.findById(10L)).thenReturn(Optional.of(agreement));
        when(users.findById(1L)).thenReturn(Optional.of(client));
        when(users.findById(2L)).thenReturn(Optional.of(freelancer));
        when(coordinator.checkAgreementParticipant(agreement, 1L))
                .thenReturn(new AgreementWorkflowCoordinator.Participant(true, false));
        when(coordinator.checkAgreementParticipant(agreement, 2L))
                .thenReturn(new AgreementWorkflowCoordinator.Participant(false, true));
    }

    @Test
    void createReviewFailsIfAgreementNotCompleted() {
        agreement.setStatus(AgreementStatus.FUNDED);

        ApiException ex = assertThrows(ApiException.class, () ->
                service.createReview(10L, 1L, new CreateReviewRequest((byte) 5, "Great")));
        assertEquals("AGREEMENT_NOT_COMPLETED", ex.getCode());
    }

    @Test
    void createReviewFailsIfAlreadyReviewed() {
        when(reviews.existsByAgreementIdAndReviewerId(10L, 1L)).thenReturn(true);

        ApiException ex = assertThrows(ApiException.class, () ->
                service.createReview(10L, 1L, new CreateReviewRequest((byte) 5, "Great")));
        assertEquals("REVIEW_ALREADY_EXISTS", ex.getCode());
    }

    @Test
    void createReviewSucceedsForClientReviewingFreelancer() {
        when(reviews.existsByAgreementIdAndReviewerId(10L, 1L)).thenReturn(false);
        when(reviews.save(any(Review.class))).thenAnswer(inv -> {
            Review r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 800L);
            return r;
        });

        ReviewResponse resp = service.createReview(10L, 1L, new CreateReviewRequest((byte) 5, "Superb developer"));

        assertNotNull(resp);
        assertEquals(800L, resp.id());
        assertEquals(5, resp.rating());
        assertEquals(1L, resp.reviewer().id());
        assertEquals(2L, resp.reviewee().id());
    }

    @Test
    void getUserReviewsComputesAverageRating() {
        Review r1 = new Review();
        ReflectionTestUtils.setField(r1, "id", 801L);
        r1.setAgreement(agreement);
        r1.setReviewer(client);
        r1.setReviewee(freelancer);
        r1.setRating((byte) 4);

        when(reviews.findByRevieweeIdOrderByCreatedAtDesc(2L)).thenReturn(List.of(r1));
        when(reviews.countByRevieweeId(2L)).thenReturn(1L);
        when(reviews.findAverageRatingForReviewee(2L)).thenReturn(4.0);

        UserReviewsResponse resp = service.getUserReviews(2L);

        assertEquals(2L, resp.userId());
        assertEquals(1L, resp.totalReviews());
        assertEquals(4.0, resp.averageRating());
        assertEquals(1, resp.reviews().size());
    }
}
