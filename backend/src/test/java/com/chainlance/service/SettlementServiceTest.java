package com.chainlance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainlance.chain.ChainReader;
import com.chainlance.dto.AcceptSettlementRequest;
import com.chainlance.dto.ProposeSettlementRequest;
import com.chainlance.dto.SettlementResponse;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.SettlementProposal;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.entity.enums.MilestoneWorkStatus;
import com.chainlance.entity.enums.SettlementStatus;
import com.chainlance.repository.MilestoneRepository;
import com.chainlance.repository.SettlementProposalRepository;
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

class SettlementServiceTest {

    private SettlementProposalRepository settlements;
    private MilestoneRepository milestones;
    private UserRepository users;
    private ChainReader chainReader;
    private AgreementWorkflowCoordinator coordinator;
    private Clock clock;
    private SettlementService service;

    private Milestone milestone;
    private Agreement agreement;
    private User client;
    private User freelancer;

    @BeforeEach
    void setUp() {
        settlements = mock(SettlementProposalRepository.class);
        milestones = mock(MilestoneRepository.class);
        users = mock(UserRepository.class);
        chainReader = mock(ChainReader.class);
        coordinator = mock(AgreementWorkflowCoordinator.class);
        clock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);

        service = new SettlementService(settlements, milestones, users, chainReader, coordinator, clock);

        client = new User();
        ReflectionTestUtils.setField(client, "id", 1L);
        freelancer = new User();
        ReflectionTestUtils.setField(freelancer, "id", 2L);

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
    }

    @Test
    void proposeSettlementFailsWhenAmountsDoNotSumToAllocation() {
        ApiException ex = assertThrows(ApiException.class, () ->
                service.proposeSettlement(100L, 2L,
                        new ProposeSettlementRequest("40000000000000000", "50000000000000000", "Reason", null)));
        assertEquals("AMOUNTS_MUST_SUM_TO_ALLOCATION", ex.getCode());
    }

    @Test
    void proposeSettlementSucceedsWhenValid() {
        when(settlements.findByMilestoneIdAndStatusIn(100L,
                List.of(SettlementStatus.AWAITING_PROPOSE_TX, SettlementStatus.ACTIVE)))
                .thenReturn(Optional.empty());
        when(settlements.save(any(SettlementProposal.class))).thenAnswer(inv -> {
            SettlementProposal p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 400L);
            return p;
        });

        SettlementResponse resp = service.proposeSettlement(100L, 2L,
                new ProposeSettlementRequest("50000000000000000", "50000000000000000", "50/50 split", null));

        assertNotNull(resp);
        assertEquals(400L, resp.id());
        assertEquals(SettlementStatus.ACTIVE, resp.status());
    }

    @Test
    void acceptSettlementFailsIfProposerAcceptsOwn() {
        SettlementProposal prop = new SettlementProposal();
        ReflectionTestUtils.setField(prop, "id", 400L);
        prop.setMilestone(milestone);
        prop.setProposer(freelancer);
        prop.setStatus(SettlementStatus.ACTIVE);

        when(settlements.findById(400L)).thenReturn(Optional.of(prop));

        ApiException ex = assertThrows(ApiException.class, () ->
                service.acceptSettlement(400L, 2L, new AcceptSettlementRequest("0x123")));
        assertEquals("CANNOT_ACCEPT_OWN_PROPOSAL", ex.getCode());
    }

    @Test
    void withdrawSettlementSucceedsForProposer() {
        SettlementProposal prop = new SettlementProposal();
        ReflectionTestUtils.setField(prop, "id", 400L);
        prop.setMilestone(milestone);
        prop.setProposer(freelancer);
        prop.setStatus(SettlementStatus.ACTIVE);

        when(settlements.findById(400L)).thenReturn(Optional.of(prop));
        when(settlements.save(any(SettlementProposal.class))).thenAnswer(inv -> inv.getArgument(0));

        SettlementResponse resp = service.withdrawSettlement(400L, 2L);
        assertEquals(SettlementStatus.WITHDRAWN, resp.status());
    }
}
