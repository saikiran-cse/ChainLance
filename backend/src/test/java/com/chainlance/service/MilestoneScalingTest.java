package com.chainlance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainlance.config.PricingProperties;
import com.chainlance.dto.MilestoneResponse;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.Project;
import com.chainlance.entity.ProjectMilestonePlan;
import com.chainlance.entity.Proposal;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.AgreementStatus;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.entity.enums.MilestoneWorkStatus;
import com.chainlance.entity.enums.Role;
import com.chainlance.repository.AgreementRepository;
import com.chainlance.repository.MilestoneRepository;
import com.chainlance.repository.ProjectMilestonePlanRepository;
import com.chainlance.web.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MilestoneScalingTest {

    private MilestoneRepository milestoneRepository;
    private AgreementRepository agreementRepository;
    private ProjectMilestonePlanRepository planRepository;
    private PriceConverter priceConverter;
    private Clock fixedClock;
    private MilestoneService milestoneService;

    @BeforeEach
    void setUp() {
        milestoneRepository = mock(MilestoneRepository.class);
        agreementRepository = mock(AgreementRepository.class);
        planRepository = mock(ProjectMilestonePlanRepository.class);
        priceConverter = new PriceConverter(new PricingProperties(new BigDecimal("250000")));
        fixedClock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);

        milestoneService = new MilestoneService(
                milestoneRepository,
                agreementRepository,
                planRepository,
                priceConverter,
                fixedClock
        );
    }

    private User createClient(Long id) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setRole(Role.CLIENT);
        return u;
    }

    private User createFreelancer(Long id) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", id);
        u.setRole(Role.FREELANCER);
        return u;
    }

    private Agreement createAgreement(Long id, User client, User freelancer, BigDecimal totalWei, BigDecimal priceInr, int durationDays) {
        Project project = new Project();
        ReflectionTestUtils.setField(project, "id", 100L);
        project.setClient(client);
        project.setTitle("Test Project");

        Proposal proposal = new Proposal();
        ReflectionTestUtils.setField(proposal, "id", 200L);
        proposal.setProject(project);
        proposal.setFreelancer(freelancer);
        proposal.setPriceInr(priceInr);
        proposal.setDurationDays(durationDays);

        Agreement agreement = new Agreement();
        ReflectionTestUtils.setField(agreement, "id", id);
        agreement.setProposal(proposal);
        agreement.setTotalWei(totalWei);
        agreement.setStatus(AgreementStatus.FINALIZED);
        ReflectionTestUtils.setField(agreement, "createdAt", fixedClock.instant());

        return agreement;
    }

    @Test
    void scalesAllocationsWithZeroLossOnPrimeTotalAndThreeMilestones() {
        User client = createClient(1L);
        User freelancer = createFreelancer(2L);
        // Prime total wei
        BigDecimal primeTotalWei = new BigDecimal("1000000000000000003");
        BigDecimal agreedPriceInr = new BigDecimal("30000.00");

        Agreement agreement = createAgreement(10L, client, freelancer, primeTotalWei, agreedPriceInr, 30);
        when(agreementRepository.findById(10L)).thenReturn(Optional.of(agreement));
        when(milestoneRepository.findByAgreementIdOrderByPositionAsc(10L)).thenReturn(List.of());

        // 3 plans: 10000, 10000, 10000 INR (equal thirds of original 30000)
        List<ProjectMilestonePlan> plans = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ProjectMilestonePlan p = new ProjectMilestonePlan();
            ReflectionTestUtils.setField(p, "id", (long) (i + 1));
            p.setProject(agreement.getProposal().getProject());
            p.setPosition(i);
            p.setDescription("Phase " + (i + 1));
            p.setAllocationInr(new BigDecimal("10000.00"));
            plans.add(p);
        }
        when(planRepository.findByProjectIdOrderByPositionAsc(100L)).thenReturn(plans);

        when(milestoneRepository.save(any(Milestone.class))).thenAnswer(invocation -> {
            Milestone m = invocation.getArgument(0);
            ReflectionTestUtils.setField(m, "id", (long) (m.getPosition() + 1));
            return m;
        });

        List<MilestoneResponse> responses = milestoneService.generateMilestones(10L, 1L);

        assertEquals(3, responses.size());
        assertEquals(0, responses.get(0).position());
        assertEquals(1, responses.get(1).position());
        assertEquals(2, responses.get(2).position());

        // Verify zero wei loss across the three milestones
        BigDecimal sumWei = BigDecimal.ZERO;
        for (MilestoneResponse resp : responses) {
            BigDecimal wei = new BigDecimal(resp.amountWei());
            assertTrue(wei.signum() > 0, "Wei allocation must be positive");
            sumWei = sumWei.add(wei);
        }
        assertEquals(primeTotalWei, sumWei, "Sum of milestone wei must equal agreement total wei exactly");

        // Verify deadline calculation
        assertNotNull(responses.get(0).effectiveDeadline());
        assertNotNull(responses.get(1).effectiveDeadline());
        assertNotNull(responses.get(2).effectiveDeadline());
        assertTrue(responses.get(0).effectiveDeadline().isBefore(responses.get(1).effectiveDeadline()));
        assertTrue(responses.get(1).effectiveDeadline().isBefore(responses.get(2).effectiveDeadline()));
        // Last deadline ends at createdAt + 30 days
        assertEquals(fixedClock.instant().plusSeconds(30L * 86400L), responses.get(2).effectiveDeadline());
    }

    @Test
    void scalesAllocationsWhenNegotiatedPriceDiffersFromPlanSum() {
        User client = createClient(1L);
        User freelancer = createFreelancer(2L);
        // Original plan sum was 1000 INR, negotiated price became 750 INR
        BigDecimal totalWei = new BigDecimal("3000000000000000"); // 750 INR / 250000 * 10^18
        BigDecimal agreedPriceInr = new BigDecimal("750.00");

        Agreement agreement = createAgreement(20L, client, freelancer, totalWei, agreedPriceInr, 14);
        when(agreementRepository.findById(20L)).thenReturn(Optional.of(agreement));
        when(milestoneRepository.findByAgreementIdOrderByPositionAsc(20L)).thenReturn(List.of());

        // Plan: 2 items of 400 and 600 INR
        ProjectMilestonePlan p1 = new ProjectMilestonePlan();
        p1.setPosition(0);
        p1.setDescription("M1");
        p1.setAllocationInr(new BigDecimal("400.00"));

        ProjectMilestonePlan p2 = new ProjectMilestonePlan();
        p2.setPosition(1);
        p2.setDescription("M2");
        p2.setAllocationInr(new BigDecimal("600.00"));

        when(planRepository.findByProjectIdOrderByPositionAsc(100L)).thenReturn(List.of(p1, p2));
        when(milestoneRepository.save(any(Milestone.class))).thenAnswer(i -> {
            Milestone m = i.getArgument(0);
            ReflectionTestUtils.setField(m, "id", (long) (m.getPosition() + 1));
            return m;
        });

        List<MilestoneResponse> responses = milestoneService.generateMilestones(20L, 1L);
        assertEquals(2, responses.size());

        BigDecimal sumWei = new BigDecimal(responses.get(0).amountWei()).add(new BigDecimal(responses.get(1).amountWei()));
        assertEquals(totalWei, sumWei);
    }

    @Test
    void generatesDefaultPhasesWhenNoPlanExists() {
        User client = createClient(1L);
        User freelancer = createFreelancer(2L);
        BigDecimal totalWei = new BigDecimal("4000000000000000");
        BigDecimal agreedPriceInr = new BigDecimal("1000.00");

        Agreement agreement = createAgreement(30L, client, freelancer, totalWei, agreedPriceInr, 7);
        when(agreementRepository.findById(30L)).thenReturn(Optional.of(agreement));
        when(milestoneRepository.findByAgreementIdOrderByPositionAsc(30L)).thenReturn(List.of());
        when(planRepository.findByProjectIdOrderByPositionAsc(100L)).thenReturn(List.of());

        when(milestoneRepository.save(any(Milestone.class))).thenAnswer(i -> {
            Milestone m = i.getArgument(0);
            ReflectionTestUtils.setField(m, "id", 1L);
            return m;
        });

        List<MilestoneResponse> responses = milestoneService.generateMilestones(30L, 1L);
        assertEquals(5, responses.size());
        assertEquals(0, responses.get(0).position());
        assertTrue(responses.size() == 5);
        assertEquals(0, responses.get(0).position());
    }

    @Test
    void isIdempotentWhenMilestonesAlreadyExist() {
        User client = createClient(1L);
        User freelancer = createFreelancer(2L);
        Agreement agreement = createAgreement(40L, client, freelancer, new BigDecimal("1000"), new BigDecimal("100"), 5);
        when(agreementRepository.findById(40L)).thenReturn(Optional.of(agreement));

        Milestone existing = new Milestone();
        ReflectionTestUtils.setField(existing, "id", 99L);
        existing.setAgreement(agreement);
        existing.setPosition(0);
        existing.setDescription("Already created");
        existing.setAmountWei(new BigDecimal("1000"));
        existing.setWorkStatus(MilestoneWorkStatus.PENDING);
        existing.setChainStatus(MilestoneChainStatus.OPEN);

        when(milestoneRepository.findByAgreementIdOrderByPositionAsc(40L)).thenReturn(List.of(existing));

        List<MilestoneResponse> responses = milestoneService.generateMilestones(40L, 1L);
        assertEquals(5, responses.size());
        assertEquals(99L, responses.get(0).id());
        verify(milestoneRepository, times(0)).save(any());
    }

    @Test
    void rejectsNonClientWithNotFound() {
        User client = createClient(1L);
        User freelancer = createFreelancer(2L);
        Agreement agreement = createAgreement(50L, client, freelancer, new BigDecimal("1000"), new BigDecimal("100"), 5);
        when(agreementRepository.findById(50L)).thenReturn(Optional.of(agreement));

        // Freelancer calling generate should get 404
        ApiException ex = assertThrows(ApiException.class, () -> milestoneService.generateMilestones(50L, 2L));
        assertEquals(404, ex.getStatus().value());

        // Stranger calling generate should get 404
        ApiException ex2 = assertThrows(ApiException.class, () -> milestoneService.generateMilestones(50L, 999L));
        assertEquals(404, ex2.getStatus().value());
    }
}

