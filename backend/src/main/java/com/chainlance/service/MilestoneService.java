package com.chainlance.service;

import com.chainlance.dto.MilestoneResponse;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.ProjectMilestonePlan;
import com.chainlance.entity.Proposal;
import com.chainlance.entity.enums.AgreementStatus;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.entity.enums.MilestoneWorkStatus;
import com.chainlance.repository.AgreementRepository;
import com.chainlance.repository.MilestoneRepository;
import com.chainlance.repository.ProjectMilestonePlanRepository;
import com.chainlance.web.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MilestoneService {

    private final MilestoneRepository milestones;
    private final AgreementRepository agreements;
    private final ProjectMilestonePlanRepository plans;
    private final PriceConverter converter;
    private final Clock clock;

    public MilestoneService(MilestoneRepository milestones, AgreementRepository agreements,
                            ProjectMilestonePlanRepository plans, PriceConverter converter, Clock clock) {
        this.milestones = milestones;
        this.agreements = agreements;
        this.plans = plans;
        this.converter = converter;
        this.clock = clock;
    }

    /**
     * Client only: generates the milestone allocations from the project milestone plan (or creates 1 milestone
     * if no plan exists). Idempotent: returns existing milestones if already generated.
     */
    @Transactional
    public List<MilestoneResponse> generateMilestones(Long agreementId, Long currentUserId) {
        Agreement agreement = agreements.findById(agreementId).orElseThrow(MilestoneService::notFoundAgreement);
        Proposal proposal = agreement.getProposal();

        if (!ProposalAccess.isClient(proposal, currentUserId)) {
            throw notFoundAgreement();
        }

        if (agreement.getStatus() == AgreementStatus.DRAFT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AGREEMENT_NOT_FINALIZED",
                    "Agreement must be finalized before generating milestones");
        }

        List<Milestone> existing = milestones.findByAgreementIdOrderByPositionAsc(agreementId);
        if (!existing.isEmpty()) {
            return existing.stream().map(m -> MilestoneResponse.from(m, converter.inrPerEth())).toList();
        }

        List<ProjectMilestonePlan> planList = plans.findByProjectIdOrderByPositionAsc(proposal.getProject().getId());
        BigDecimal agreedWei = agreement.getTotalWei();
        BigDecimal agreedInr = proposal.getPriceInr();
        Instant agreedAt = agreement.getCreatedAt() != null ? agreement.getCreatedAt() : Instant.now(clock);
        int totalDays = proposal.getDurationDays();

        List<Milestone> created = new ArrayList<>();

        if (planList.isEmpty()) {
            planList = buildDefaultPlan(proposal.getProject().getTitle(), agreedInr, totalDays);
        }
            int n = planList.size();
            if (n > 20) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TOO_MANY_MILESTONES", "Maximum 20 milestones allowed");
            }

            BigDecimal sumPlanInr = planList.stream()
                    .map(ProjectMilestonePlan::getAllocationInr)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (sumPlanInr.signum() <= 0) {
                sumPlanInr = agreedInr;
            }

            // Step 1: scale INR allocations
            BigDecimal[] milestoneInrs = new BigDecimal[n];
            BigDecimal inrAccumulator = BigDecimal.ZERO;
            for (int i = 0; i < n; i++) {
                if (i == n - 1) {
                    milestoneInrs[i] = agreedInr.subtract(inrAccumulator);
                } else {
                    milestoneInrs[i] = agreedInr.multiply(planList.get(i).getAllocationInr())
                            .divide(sumPlanInr, 2, RoundingMode.DOWN);
                    inrAccumulator = inrAccumulator.add(milestoneInrs[i]);
                }
                if (milestoneInrs[i].signum() <= 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ALLOCATION",
                            "Scaled milestone allocation must be greater than zero");
                }
            }

            // Step 2: scale Wei allocations
            BigDecimal[] milestoneWeis = new BigDecimal[n];
            BigDecimal weiAccumulator = BigDecimal.ZERO;
            for (int i = 0; i < n; i++) {
                if (i == n - 1) {
                    milestoneWeis[i] = agreedWei.subtract(weiAccumulator);
                } else {
                    milestoneWeis[i] = agreedWei.multiply(milestoneInrs[i])
                            .divide(agreedInr, 0, RoundingMode.DOWN);
                    weiAccumulator = weiAccumulator.add(milestoneWeis[i]);
                }
                if (milestoneWeis[i].signum() <= 0) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ALLOCATION",
                            "Milestone wei allocation must be greater than zero");
                }
            }

            // Step 3: deadlines & milestones
            BigDecimal cumulativeInr = BigDecimal.ZERO;
            for (int i = 0; i < n; i++) {
                ProjectMilestonePlan plan = planList.get(i);
                cumulativeInr = cumulativeInr.add(milestoneInrs[i]);
                long cumDays;
                if (i == n - 1) {
                    cumDays = Math.max(1, totalDays);
                } else {
                    cumDays = Math.max(1, Math.min(totalDays,
                            cumulativeInr.multiply(BigDecimal.valueOf(totalDays))
                                    .divide(agreedInr, 0, RoundingMode.HALF_UP).longValue()));
                }
                Instant deadline = agreedAt.plus(Duration.ofDays(cumDays));

                Milestone m = new Milestone();
                m.setAgreement(agreement);
                m.setPosition(i); // 0, 1, ..., n-1
                m.setDescription(plan.getDescription());
                m.setAmountWei(milestoneWeis[i]);
                m.setOriginalDeadline(deadline);
                m.setEffectiveDeadline(deadline);
                m.setWorkStatus(MilestoneWorkStatus.PENDING);
                m.setChainStatus(MilestoneChainStatus.OPEN);
                m.setFreelancerPaidWei(BigDecimal.ZERO);
                m.setClientRefundedWei(BigDecimal.ZERO);

                created.add(milestones.save(m));
            }

        return created.stream().map(m -> MilestoneResponse.from(m, converter.inrPerEth())).toList();
    }

    @Transactional(readOnly = true)
    public List<MilestoneResponse> getMilestones(Long agreementId, Long currentUserId) {
        Agreement agreement = agreements.findById(agreementId).orElseThrow(MilestoneService::notFoundAgreement);
        Proposal proposal = agreement.getProposal();

        if (!ProposalAccess.isClient(proposal, currentUserId) && !ProposalAccess.isFreelancer(proposal, currentUserId)) {
            throw notFoundAgreement();
        }

        return milestones.findByAgreementIdOrderByPositionAsc(agreementId).stream()
                .map(m -> MilestoneResponse.from(m, converter.inrPerEth()))
                .toList();
    }

    @Transactional(readOnly = true)
    public MilestoneResponse getMilestone(Long milestoneId, Long currentUserId) {
        Milestone milestone = milestones.findById(milestoneId).orElseThrow(MilestoneService::notFoundMilestone);
        Proposal proposal = milestone.getAgreement().getProposal();

        if (!ProposalAccess.isClient(proposal, currentUserId) && !ProposalAccess.isFreelancer(proposal, currentUserId)) {
            throw notFoundMilestone();
        }

        return MilestoneResponse.from(milestone, converter.inrPerEth());
    }

    private static ApiException notFoundAgreement() {
        return new ApiException(HttpStatus.NOT_FOUND, "AGREEMENT_NOT_FOUND", "Agreement not found");
    }

    private static ApiException notFoundMilestone() {
        return new ApiException(HttpStatus.NOT_FOUND, "MILESTONE_NOT_FOUND", "Milestone not found");
    }

    private List<ProjectMilestonePlan> buildDefaultPlan(String title, BigDecimal agreedInr, int totalDays) {
        List<ProjectMilestonePlan> defaultPlans = new ArrayList<>();
        // Big projects (> 75,000 INR or > 30 days): 6 engineering phases
        // Standard projects: 5 core engineering phases (Contracts, Backend, Frontend, Testing, Deployment)
        if (agreedInr.compareTo(new BigDecimal("75000")) > 0 || totalDays > 30) {
            defaultPlans.add(newPlan(0, "Phase 1: Architecture & Technical Specification", 10));
            defaultPlans.add(newPlan(1, "Phase 2: Solidity Smart Contracts & Protocol Invariants", 20));
            defaultPlans.add(newPlan(2, "Phase 3: Backend Workflow Engine & Database Services", 25));
            defaultPlans.add(newPlan(3, "Phase 4: Non-Custodial Frontend & Wallet Integration", 20));
            defaultPlans.add(newPlan(4, "Phase 5: Unit & End-to-End Integration Testing", 15));
            defaultPlans.add(newPlan(5, "Phase 6: Final Testnet Verification & Staging Deployment", 10));
        } else {
            defaultPlans.add(newPlan(0, "Milestone 1: Smart Contracts & Escrow Protocols", 25));
            defaultPlans.add(newPlan(1, "Milestone 2: Backend Architecture & Workflow APIs", 20));
            defaultPlans.add(newPlan(2, "Milestone 3: Frontend Web3 & MetaMask Integration", 25));
            defaultPlans.add(newPlan(3, "Milestone 4: End-to-End Testing & Verification", 15));
            defaultPlans.add(newPlan(4, "Milestone 5: Staging & Deployment", 15));
        }
        return defaultPlans;
    }

    private static ProjectMilestonePlan newPlan(int position, String description, int percent) {
        ProjectMilestonePlan p = new ProjectMilestonePlan();
        p.setPosition(position);
        p.setDescription(description);
        p.setAllocationInr(BigDecimal.valueOf(percent));
        return p;
    }
}
