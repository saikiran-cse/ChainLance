package com.chainlance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainlance.chain.ChainReader;
import com.chainlance.dto.AdminDecisionRequest;
import com.chainlance.dto.DisputeDetailResponse;
import com.chainlance.dto.DisputeReplyRequest;
import com.chainlance.dto.DisputeReplyResponse;
import com.chainlance.dto.OpenDisputeRequest;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.Dispute;
import com.chainlance.entity.DisputeResponse;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.DisputeDecision;
import com.chainlance.entity.enums.DisputeStatus;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.repository.DisputeRepository;
import com.chainlance.repository.DisputeResponseRepository;
import com.chainlance.repository.MilestoneRepository;
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

class DisputeServiceTest {

    private DisputeRepository disputes;
    private DisputeResponseRepository disputeResponses;
    private MilestoneRepository milestones;
    private UserRepository users;
    private ChainReader chainReader;
    private AgreementWorkflowCoordinator coordinator;
    private Clock clock;
    private DisputeService service;

    private Milestone milestone;
    private Agreement agreement;
    private User client;
    private User freelancer;
    private User admin;

    @BeforeEach
    void setUp() {
        disputes = mock(DisputeRepository.class);
        disputeResponses = mock(DisputeResponseRepository.class);
        milestones = mock(MilestoneRepository.class);
        users = mock(UserRepository.class);
        chainReader = mock(ChainReader.class);
        coordinator = mock(AgreementWorkflowCoordinator.class);
        clock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);

        service = new DisputeService(disputes, disputeResponses, milestones, users, chainReader, coordinator, clock);

        client = new User();
        ReflectionTestUtils.setField(client, "id", 1L);
        freelancer = new User();
        ReflectionTestUtils.setField(freelancer, "id", 2L);
        admin = new User();
        ReflectionTestUtils.setField(admin, "id", 99L);

        agreement = new Agreement();
        ReflectionTestUtils.setField(agreement, "id", 10L);

        milestone = new Milestone();
        ReflectionTestUtils.setField(milestone, "id", 100L);
        milestone.setAgreement(agreement);
        milestone.setChainStatus(MilestoneChainStatus.OPEN);
        milestone.setAmountWei(new BigDecimal("100000000000000000"));

        when(milestones.findById(100L)).thenReturn(Optional.of(milestone));
        when(users.findById(1L)).thenReturn(Optional.of(client));
        when(users.findById(2L)).thenReturn(Optional.of(freelancer));
        when(users.findById(99L)).thenReturn(Optional.of(admin));
    }

    @Test
    void openDisputeFailsWhenAlreadyActive() {
        when(disputes.findByMilestoneIdAndStatusIn(100L,
                List.of(DisputeStatus.AWAITING_OPEN_TX, DisputeStatus.OPEN, DisputeStatus.DECIDED)))
                .thenReturn(Optional.of(new Dispute()));

        ApiException ex = assertThrows(ApiException.class, () ->
                service.openDispute(100L, 1L, new OpenDisputeRequest("Incomplete", null)));
        assertEquals("DISPUTE_ALREADY_ACTIVE", ex.getCode());
    }

    @Test
    void openDisputeSucceedsOffChain() {
        when(disputes.findByMilestoneIdAndStatusIn(100L,
                List.of(DisputeStatus.AWAITING_OPEN_TX, DisputeStatus.OPEN, DisputeStatus.DECIDED)))
                .thenReturn(Optional.empty());
        when(disputes.save(any(Dispute.class))).thenAnswer(inv -> {
            Dispute d = inv.getArgument(0);
            ReflectionTestUtils.setField(d, "id", 600L);
            return d;
        });

        DisputeDetailResponse resp = service.openDispute(100L, 1L, new OpenDisputeRequest("Reason", null));

        assertNotNull(resp);
        assertEquals(600L, resp.id());
        assertEquals(DisputeStatus.OPEN, resp.status());
        assertEquals(MilestoneChainStatus.DISPUTED, milestone.getChainStatus());
        verify(milestones).save(milestone);
    }

    @Test
    void replyToDisputeSucceeds() {
        Dispute dispute = new Dispute();
        ReflectionTestUtils.setField(dispute, "id", 600L);
        dispute.setMilestone(milestone);
        dispute.setStatus(DisputeStatus.OPEN);

        when(disputes.findById(600L)).thenReturn(Optional.of(dispute));
        when(disputeResponses.save(any(DisputeResponse.class))).thenAnswer(inv -> {
            DisputeResponse r = inv.getArgument(0);
            ReflectionTestUtils.setField(r, "id", 700L);
            return r;
        });

        DisputeReplyResponse resp = service.replyToDispute(600L, 2L, false,
                new DisputeReplyRequest("Rebuttal text", "https://link"));

        assertNotNull(resp);
        assertEquals(700L, resp.id());
        assertEquals("Rebuttal text", resp.response());
    }

    @Test
    void adminRecordDecisionValidatesSplitSum() {
        Dispute dispute = new Dispute();
        ReflectionTestUtils.setField(dispute, "id", 600L);
        dispute.setMilestone(milestone);
        dispute.setStatus(DisputeStatus.OPEN);

        when(disputes.findById(600L)).thenReturn(Optional.of(dispute));

        ApiException ex = assertThrows(ApiException.class, () ->
                service.adminRecordDecision(600L, 99L, new AdminDecisionRequest(
                        DisputeDecision.SPLIT, "40000000000000000", "50000000000000000", "Explanation"
                )));
        assertEquals("AMOUNTS_MUST_SUM_TO_ALLOCATION", ex.getCode());
    }

    @Test
    void adminRecordDecisionSucceedsForFreelancerFull() {
        Dispute dispute = new Dispute();
        ReflectionTestUtils.setField(dispute, "id", 600L);
        dispute.setMilestone(milestone);
        dispute.setStatus(DisputeStatus.OPEN);

        when(disputes.findById(600L)).thenReturn(Optional.of(dispute));
        when(disputes.save(any(Dispute.class))).thenAnswer(inv -> inv.getArgument(0));

        DisputeDetailResponse resp = service.adminRecordDecision(600L, 99L, new AdminDecisionRequest(
                DisputeDecision.FREELANCER_FULL, null, null, "Deliverable was satisfactory"
        ));

        assertEquals(DisputeStatus.DECIDED, resp.status());
        assertEquals(DisputeDecision.FREELANCER_FULL, resp.adminDecision());
        assertEquals("100000000000000000", resp.adminFreelancerWei());
        assertEquals("0", resp.adminClientWei());
    }
}
