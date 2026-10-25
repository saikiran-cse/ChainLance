package com.chainlance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainlance.dto.CreateExtensionRequest;
import com.chainlance.dto.DecisionRequest;
import com.chainlance.dto.ExtensionResponse;
import com.chainlance.entity.DeadlineExtensionRequest;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.ExtensionStatus;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.entity.enums.MilestoneWorkStatus;
import com.chainlance.repository.DeadlineExtensionRequestRepository;
import com.chainlance.repository.MilestoneRepository;
import com.chainlance.repository.UserRepository;
import com.chainlance.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ExtensionServiceTest {

    private DeadlineExtensionRequestRepository extensions;
    private MilestoneRepository milestones;
    private UserRepository users;
    private AgreementWorkflowCoordinator coordinator;
    private Clock clock;
    private ExtensionService service;

    private Milestone milestone;
    private User freelancer;
    private User client;

    @BeforeEach
    void setUp() {
        extensions = mock(DeadlineExtensionRequestRepository.class);
        milestones = mock(MilestoneRepository.class);
        users = mock(UserRepository.class);
        coordinator = mock(AgreementWorkflowCoordinator.class);
        clock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);

        service = new ExtensionService(extensions, milestones, users, coordinator, clock);

        freelancer = new User();
        ReflectionTestUtils.setField(freelancer, "id", 2L);
        client = new User();
        ReflectionTestUtils.setField(client, "id", 1L);

        milestone = new Milestone();
        ReflectionTestUtils.setField(milestone, "id", 100L);
        milestone.setWorkStatus(MilestoneWorkStatus.IN_PROGRESS);
        milestone.setChainStatus(MilestoneChainStatus.OPEN);
        milestone.setEffectiveDeadline(Instant.parse("2026-10-26T12:00:00Z"));

        when(milestones.findById(100L)).thenReturn(Optional.of(milestone));
        when(users.findById(2L)).thenReturn(Optional.of(freelancer));
        when(users.findById(1L)).thenReturn(Optional.of(client));
    }

    @Test
    void requestExtensionSucceeds() {
        when(coordinator.checkMilestoneParticipant(milestone, 2L))
                .thenReturn(new AgreementWorkflowCoordinator.Participant(false, true));
        when(extensions.findByMilestoneIdAndStatus(100L, ExtensionStatus.PENDING))
                .thenReturn(Optional.empty());
        when(extensions.save(any(DeadlineExtensionRequest.class))).thenAnswer(inv -> {
            DeadlineExtensionRequest e = inv.getArgument(0);
            ReflectionTestUtils.setField(e, "id", 300L);
            return e;
        });

        Instant requestedDeadline = Instant.parse("2026-11-05T12:00:00Z");
        ExtensionResponse resp = service.requestExtension(100L, 2L,
                new CreateExtensionRequest(requestedDeadline, "Need extra time"));

        assertNotNull(resp);
        assertEquals(300L, resp.id());
        assertEquals(ExtensionStatus.PENDING, resp.status());
    }

    @Test
    void requestExtensionFailsIfAlreadyPending() {
        when(coordinator.checkMilestoneParticipant(milestone, 2L))
                .thenReturn(new AgreementWorkflowCoordinator.Participant(false, true));
        when(extensions.findByMilestoneIdAndStatus(100L, ExtensionStatus.PENDING))
                .thenReturn(Optional.of(new DeadlineExtensionRequest()));

        ApiException ex = assertThrows(ApiException.class, () ->
                service.requestExtension(100L, 2L,
                        new CreateExtensionRequest(Instant.parse("2026-11-05T12:00:00Z"), "Another")));
        assertEquals("EXTENSION_ALREADY_PENDING", ex.getCode());
    }

    @Test
    void approveExtensionUpdatesMilestoneDeadline() {
        DeadlineExtensionRequest ext = new DeadlineExtensionRequest();
        ReflectionTestUtils.setField(ext, "id", 300L);
        ext.setMilestone(milestone);
        ext.setStatus(ExtensionStatus.PENDING);
        Instant newDeadline = Instant.parse("2026-11-05T12:00:00Z");
        ext.setRequestedDeadline(newDeadline);

        when(extensions.findById(300L)).thenReturn(Optional.of(ext));
        when(coordinator.checkMilestoneParticipant(milestone, 1L))
                .thenReturn(new AgreementWorkflowCoordinator.Participant(true, false));

        ExtensionResponse resp = service.approveExtension(300L, 1L, new DecisionRequest("Approved"));

        assertEquals(ExtensionStatus.APPROVED, resp.status());
        assertEquals(newDeadline, milestone.getEffectiveDeadline());
        verify(milestones).save(milestone);
        verify(extensions).save(ext);
    }
}
