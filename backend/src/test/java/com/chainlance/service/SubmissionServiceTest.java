package com.chainlance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainlance.chain.ChainReader;
import com.chainlance.dto.ApproveSubmissionRequest;
import com.chainlance.dto.RevisionRequest;
import com.chainlance.dto.SubmissionResponse;
import com.chainlance.dto.SubmitWorkRequest;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.Project;
import com.chainlance.entity.Proposal;
import com.chainlance.entity.Submission;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.AgreementStatus;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.entity.enums.MilestoneWorkStatus;
import com.chainlance.entity.enums.SubmissionReviewStatus;
import com.chainlance.repository.MilestoneRepository;
import com.chainlance.repository.SubmissionRepository;
import com.chainlance.repository.UserRepository;
import com.chainlance.web.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SubmissionServiceTest {

    private SubmissionRepository submissions;
    private MilestoneRepository milestones;
    private UserRepository users;
    private ChainReader chainReader;
    private AgreementWorkflowCoordinator coordinator;
    private Clock clock;
    private SubmissionService service;

    private User client;
    private User freelancer;
    private Agreement agreement;
    private Milestone milestone;

    @BeforeEach
    void setUp() {
        submissions = mock(SubmissionRepository.class);
        milestones = mock(MilestoneRepository.class);
        users = mock(UserRepository.class);
        chainReader = mock(ChainReader.class);
        coordinator = mock(AgreementWorkflowCoordinator.class);
        clock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);

        service = new SubmissionService(submissions, milestones, users, chainReader, coordinator, clock);

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
        agreement.setStatus(AgreementStatus.FUNDED);

        milestone = new Milestone();
        ReflectionTestUtils.setField(milestone, "id", 100L);
        milestone.setAgreement(agreement);
        milestone.setPosition(0);
        milestone.setAmountWei(new BigDecimal("100000000000000000"));
        milestone.setWorkStatus(MilestoneWorkStatus.IN_PROGRESS);
        milestone.setChainStatus(MilestoneChainStatus.OPEN);
        milestone.setEffectiveDeadline(Instant.parse("2026-10-26T12:00:00Z"));

        when(milestones.findById(100L)).thenReturn(Optional.of(milestone));
        when(coordinator.checkMilestoneParticipant(milestone, 2L))
                .thenReturn(new AgreementWorkflowCoordinator.Participant(false, true));
        when(coordinator.checkMilestoneParticipant(milestone, 1L))
                .thenReturn(new AgreementWorkflowCoordinator.Participant(true, false));
    }

    @Test
    void submitWorkSucceedsWhenOnTime() {
        when(submissions.save(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 500L);
            return s;
        });

        SubmissionResponse resp = service.submitWork(100L, 2L,
                new SubmitWorkRequest("Code delivery", "https://github.com/pr/1"));

        assertNotNull(resp);
        assertEquals(500L, resp.id());
        assertFalse(resp.late());
        assertEquals(SubmissionReviewStatus.PENDING, resp.reviewStatus());
        assertEquals(MilestoneWorkStatus.SUBMITTED, milestone.getWorkStatus());
        verify(milestones).save(milestone);
    }

    @Test
    void submitWorkMarksLateWhenPastEffectiveDeadline() {
        milestone.setEffectiveDeadline(Instant.parse("2026-10-24T12:00:00Z")); // in past relative to clock
        when(submissions.save(any(Submission.class))).thenAnswer(inv -> {
            Submission s = inv.getArgument(0);
            ReflectionTestUtils.setField(s, "id", 501L);
            return s;
        });

        SubmissionResponse resp = service.submitWork(100L, 2L,
                new SubmitWorkRequest("Late delivery", "https://github.com/pr/2"));

        assertTrue(resp.late());
    }

    @Test
    void submitWorkFailsIfNotFreelancer() {
        when(coordinator.checkMilestoneParticipant(milestone, 1L))
                .thenReturn(new AgreementWorkflowCoordinator.Participant(true, false));

        ApiException ex = assertThrows(ApiException.class, () ->
                service.submitWork(100L, 1L, new SubmitWorkRequest("desc", null)));
        assertEquals("FORBIDDEN", ex.getCode());
    }

    @Test
    void submitWorkFailsIfAgreementNotFunded() {
        agreement.setStatus(AgreementStatus.FINALIZED);
        ApiException ex = assertThrows(ApiException.class, () ->
                service.submitWork(100L, 2L, new SubmitWorkRequest("desc", null)));
        assertEquals("AGREEMENT_NOT_FUNDED", ex.getCode());
    }

    @Test
    void requestRevisionUpdatesSubmissionAndMilestone() {
        Submission sub = new Submission();
        ReflectionTestUtils.setField(sub, "id", 500L);
        sub.setMilestone(milestone);
        sub.setReviewStatus(SubmissionReviewStatus.PENDING);
        milestone.setWorkStatus(MilestoneWorkStatus.SUBMITTED);

        when(submissions.findById(500L)).thenReturn(Optional.of(sub));

        SubmissionResponse resp = service.requestRevision(500L, 1L, new RevisionRequest("Fix unit tests"));

        assertEquals(SubmissionReviewStatus.REVISION_REQUESTED, resp.reviewStatus());
        assertEquals("Fix unit tests", resp.revisionReason());
        assertEquals(MilestoneWorkStatus.REVISION_REQUESTED, milestone.getWorkStatus());
        verify(submissions).save(sub);
        verify(milestones).save(milestone);
    }

    @Test
    void approveSubmissionOffChainUpdatesStatuses() {
        Submission sub = new Submission();
        ReflectionTestUtils.setField(sub, "id", 500L);
        sub.setMilestone(milestone);
        sub.setReviewStatus(SubmissionReviewStatus.PENDING);
        milestone.setWorkStatus(MilestoneWorkStatus.SUBMITTED);

        when(submissions.findById(500L)).thenReturn(Optional.of(sub));
        when(users.findById(1L)).thenReturn(Optional.of(client));

        SubmissionResponse resp = service.approveSubmission(500L, 1L, new ApproveSubmissionRequest(null));

        assertEquals(SubmissionReviewStatus.APPROVED, resp.reviewStatus());
        assertEquals(MilestoneWorkStatus.APPROVED, milestone.getWorkStatus());
        assertEquals(MilestoneChainStatus.RELEASED, milestone.getChainStatus());
        verify(coordinator).advanceNextMilestoneOrComplete(agreement, milestone);
    }
}
