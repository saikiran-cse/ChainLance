package com.chainlance.service;

import com.chainlance.dto.AgreementResponse;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.NegotiationOffer;
import com.chainlance.entity.Project;
import com.chainlance.entity.ProjectMilestonePlan;
import com.chainlance.entity.Proposal;
import com.chainlance.entity.User;
import com.chainlance.entity.WalletAddress;
import com.chainlance.entity.enums.AgreementStatus;
import com.chainlance.entity.enums.OfferStatus;
import com.chainlance.entity.enums.ProjectStatus;
import com.chainlance.entity.enums.ProposalStatus;
import com.chainlance.repository.AgreementRepository;
import com.chainlance.repository.NegotiationOfferRepository;
import com.chainlance.repository.ProjectMilestonePlanRepository;
import com.chainlance.repository.ProjectRepository;
import com.chainlance.repository.ProposalRepository;
import com.chainlance.repository.WalletAddressRepository;
import com.chainlance.wallet.EthereumAddress;
import com.chainlance.web.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accepting a proposal and creating its agreement snapshot happen in ONE transaction:
 * either the proposal becomes ACCEPTED, the project IN_PROGRESS, the other proposals REJECTED and the snapshot is
 * stored, or nothing changes. There is deliberately no method that edits an existing agreement.
 */
@Service
public class AgreementService {

    static final int SNAPSHOT_SCHEMA_VERSION = 1;

    private final ProposalAccess access;
    private final AgreementRepository agreements;
    private final NegotiationOfferRepository offers;
    private final ProjectMilestonePlanRepository plans;
    private final WalletAddressRepository wallets;
    private final ProposalRepository proposals;
    private final ProjectRepository projects;
    private final ProposalWorkflow workflow;
    private final SnapshotHasher hasher;
    private final PriceConverter converter;
    private final Clock clock;

    public AgreementService(ProposalAccess access, AgreementRepository agreements, NegotiationOfferRepository offers,
                            ProjectMilestonePlanRepository plans, WalletAddressRepository wallets,
                            ProposalRepository proposals, ProjectRepository projects, ProposalWorkflow workflow,
                            SnapshotHasher hasher, PriceConverter converter, Clock clock) {
        this.access = access;
        this.agreements = agreements;
        this.offers = offers;
        this.plans = plans;
        this.wallets = wallets;
        this.proposals = proposals;
        this.projects = projects;
        this.workflow = workflow;
        this.hasher = hasher;
        this.converter = converter;
        this.clock = clock;
    }

    @Transactional
    public AgreementResponse acceptProposal(Long proposalId, Long clientId) {
        // Locks the proposal row: a second click or a parallel request waits here and then sees the new state.
        Proposal proposal = access.loadForClient(proposalId, clientId, true);
        Project project = proposal.getProject();

        // ---- every check first, so a refusal changes nothing
        if (proposal.getStatus() == ProposalStatus.ACCEPTED || agreements.findByProposalId(proposalId).isPresent()) {
            throw conflict("AGREEMENT_EXISTS", "An agreement already exists for this proposal");
        }
        ProposalTransitions.require(proposal.getStatus(), ProposalStatus.ACCEPTED);
        if (project.getStatus() != ProjectStatus.OPEN) {
            throw conflict("PROJECT_NOT_OPEN", "The project is " + project.getStatus() + ", not open");
        }
        ProjectTransitions.require(project.getStatus(), ProjectStatus.IN_PROGRESS);
        if (offers.existsByProposalIdAndStatus(proposalId, OfferStatus.PENDING)) {
            throw conflict("OFFER_PENDING", "An offer is still waiting for an answer. Finish the negotiation first");
        }
        NegotiationOffer agreed = offers.findFirstByProposalIdAndStatus(proposalId, OfferStatus.ACCEPTED)
                .orElseThrow(() -> conflict("TERMS_NOT_AGREED",
                        "The final price has not been agreed yet. One party must accept the other party's offer"));

        BigDecimal priceInr = agreed.getPriceInr().setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalWei = converter.toWei(priceInr);
        if (totalWei.signum() <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PRICE_TOO_SMALL", "The agreed price is too small to convert");
        }

        // ---- build the frozen snapshot
        User client = project.getClient();
        User freelancer = proposal.getFreelancer();
        String clientWallet = firstWallet(client.getId());
        String freelancerWallet = firstWallet(freelancer.getId());
        Instant now = Instant.now(clock);
        Map<String, Object> snapshot = buildSnapshot(proposal, project, client, freelancer, agreed, priceInr, totalWei,
                clientWallet, freelancerWallet, now);

        Agreement agreement = new Agreement();
        agreement.setProposal(proposal);
        agreement.setTotalWei(totalWei);
        agreement.setStatus(AgreementStatus.FINALIZED);
        agreement.setClientFinalizedAt(finalizedAt(agreed, client.getId(), now));
        agreement.setFreelancerFinalizedAt(finalizedAt(agreed, freelancer.getId(), now));
        agreement.setSnapshot(snapshot);
        agreement.setSnapshotHash(hasher.hash(snapshot));
        agreement.setClientWallet(clientWallet);
        agreement.setFreelancerWallet(freelancerWallet);
        try {
            agreement = agreements.saveAndFlush(agreement); // UNIQUE(proposal_id) is the last line of defence
        } catch (DataIntegrityViolationException e) {
            throw conflict("AGREEMENT_EXISTS", "An agreement already exists for this proposal");
        }

        // ---- the rest of the state change, in the same transaction
        proposal.setStatus(ProposalStatus.ACCEPTED);
        proposals.saveAndFlush(proposal);
        project.setStatus(ProjectStatus.IN_PROGRESS);
        projects.saveAndFlush(project);
        workflow.rejectOpenProposals(project.getId(), proposal.getId());

        return AgreementResponse.from(agreement, hasher.matches(agreement.getSnapshot(), agreement.getSnapshotHash()));
    }

    @Transactional(readOnly = true)
    public List<AgreementResponse> mine(Long userId) {
        return agreements.findAllForParticipant(userId).stream().map(this::toResponse).toList();
    }

    /** Only the agreement's own client or freelancer. Anyone else gets 404. */
    @Transactional(readOnly = true)
    public AgreementResponse get(Long agreementId, Long userId) {
        Agreement agreement = agreements.findById(agreementId).orElseThrow(AgreementService::notFound);
        Proposal proposal = agreement.getProposal();
        if (!ProposalAccess.isClient(proposal, userId) && !ProposalAccess.isFreelancer(proposal, userId)) {
            throw notFound();
        }
        return toResponse(agreement);
    }

    /**
     * Links a verified wallet address to the agreement for the caller.
     * Can be changed until the escrow is registered on-chain.
     */
    @Transactional
    public AgreementResponse linkWallet(Long agreementId, Long userId, String address) {
        String normalized = EthereumAddress.normalize(address);
        Agreement agreement = agreements.findById(agreementId).orElseThrow(AgreementService::notFound);
        Proposal proposal = agreement.getProposal();
        boolean isClient = ProposalAccess.isClient(proposal, userId);
        boolean isFreelancer = ProposalAccess.isFreelancer(proposal, userId);
        if (!isClient && !isFreelancer) {
            throw notFound();
        }
        if (agreement.getEscrowAddress() != null) {
            throw conflict("ESCROW_ALREADY_REGISTERED", "Wallet cannot be changed after escrow is registered");
        }
        if (!wallets.existsByAddressAndUserId(normalized, userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WALLET_NOT_LINKED",
                    "Wallet " + normalized + " is not a verified wallet of the current user");
        }
        Instant now = Instant.now(clock);
        if (isClient) {
            agreement.setClientWallet(normalized);
            agreement.setClientFinalizedAt(now);
        } else {
            agreement.setFreelancerWallet(normalized);
            agreement.setFreelancerFinalizedAt(now);
        }
        Agreement saved = agreements.save(agreement);
        return toResponse(saved);
    }

    // ------------------------------------------------------------------ helpers

    private AgreementResponse toResponse(Agreement agreement) {
        return AgreementResponse.from(agreement, hasher.matches(agreement.getSnapshot(), agreement.getSnapshotHash()));
    }

    private String firstWallet(Long userId) {
        List<WalletAddress> list = wallets.findByUserIdOrderByCreatedAtAsc(userId);
        return list.isEmpty() ? null : list.get(0).getAddress();
    }

    /** The author finalized when they made the accepted offer, the other party when they accepted it. */
    private static Instant finalizedAt(NegotiationOffer agreed, Long userId, Instant fallback) {
        Instant value = agreed.getOfferedBy().getId().equals(userId) ? agreed.getCreatedAt() : agreed.getRespondedAt();
        return value != null ? value : fallback;
    }

    /**
     * Only text, whole numbers, maps and lists go in, and null values are left out, so the stored JSON gives the
     * same hash when it is read back from MySQL. Money is always a string with 2 decimals.
     */
    private Map<String, Object> buildSnapshot(Proposal proposal, Project project, User client, User freelancer,
                                              NegotiationOffer agreed, BigDecimal priceInr, BigDecimal totalWei,
                                              String clientWallet, String freelancerWallet, Instant agreedAt) {
        Map<String, Object> projectPart = new LinkedHashMap<>();
        projectPart.put("id", project.getId());
        projectPart.put("title", project.getTitle());
        projectPart.put("description", project.getDescription());
        projectPart.put("category", project.getCategory());
        putIfPresent(projectPart, "skills", project.getSkills());
        projectPart.put("budgetInr", money(project.getBudgetInr()));
        if (project.getDeadline() != null) {
            projectPart.put("deadline", project.getDeadline().toString());
        }
        List<Map<String, Object>> milestonePlan = new ArrayList<>();
        for (ProjectMilestonePlan step : plans.findByProjectIdOrderByPositionAsc(project.getId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("position", step.getPosition());
            row.put("description", step.getDescription());
            row.put("allocationInr", money(step.getAllocationInr()));
            milestonePlan.add(row);
        }
        if (!milestonePlan.isEmpty()) {
            projectPart.put("milestonePlan", milestonePlan);
        }

        Map<String, Object> clientPart = new LinkedHashMap<>();
        clientPart.put("id", client.getId());
        clientPart.put("displayName", client.getDisplayName());
        putIfPresent(clientPart, "wallet", clientWallet);

        Map<String, Object> freelancerPart = new LinkedHashMap<>();
        freelancerPart.put("id", freelancer.getId());
        freelancerPart.put("displayName", freelancer.getDisplayName());
        putIfPresent(freelancerPart, "wallet", freelancerWallet);

        Map<String, Object> proposalPart = new LinkedHashMap<>();
        proposalPart.put("id", proposal.getId());
        proposalPart.put("message", proposal.getMessage());
        proposalPart.put("originalPriceInr", money(proposal.getPriceInr()));
        proposalPart.put("originalDurationDays", proposal.getDurationDays());
        putIfPresent(proposalPart, "portfolioLinks", proposal.getPortfolioLinks());

        Map<String, Object> termsPart = new LinkedHashMap<>();
        termsPart.put("offerId", agreed.getId());
        termsPart.put("offeredByUserId", agreed.getOfferedBy().getId());
        termsPart.put("priceInr", money(priceInr));
        termsPart.put("durationDays", agreed.getDurationDays());
        putIfPresent(termsPart, "note", agreed.getNote());

        Map<String, Object> pricingPart = new LinkedHashMap<>();
        pricingPart.put("referenceCurrency", "INR");
        pricingPart.put("settlementCurrency", "ETH");
        pricingPart.put("inrPerEth", converter.inrPerEth().stripTrailingZeros().toPlainString());
        pricingPart.put("totalWei", totalWei.toBigInteger().toString());

        List<Map<String, Object>> history = new ArrayList<>();
        for (NegotiationOffer offer : offers.findByProposalIdOrderByCreatedAtAscIdAsc(proposal.getId())) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("offerId", offer.getId());
            row.put("offeredByUserId", offer.getOfferedBy().getId());
            row.put("priceInr", money(offer.getPriceInr()));
            row.put("durationDays", offer.getDurationDays());
            row.put("status", offer.getStatus().name());
            row.put("createdAt", offer.getCreatedAt().toString());
            if (offer.getRespondedAt() != null) {
                row.put("respondedAt", offer.getRespondedAt().toString());
            }
            putIfPresent(row, "note", offer.getNote());
            history.add(row);
        }

        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", SNAPSHOT_SCHEMA_VERSION);
        snapshot.put("agreedAt", agreedAt.toString());
        snapshot.put("project", projectPart);
        snapshot.put("client", clientPart);
        snapshot.put("freelancer", freelancerPart);
        snapshot.put("proposal", proposalPart);
        snapshot.put("agreedTerms", termsPart);
        snapshot.put("pricing", pricingPart);
        snapshot.put("negotiationHistory", history);
        return snapshot;
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "AGREEMENT_NOT_FOUND", "Agreement not found");
    }
}
