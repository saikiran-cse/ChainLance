package com.chainlance.service;

import com.chainlance.dto.OfferResponse;
import com.chainlance.dto.ProposalResponse;
import com.chainlance.dto.SubmitProposalRequest;
import com.chainlance.entity.NegotiationOffer;
import com.chainlance.entity.Project;
import com.chainlance.entity.Proposal;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.ProjectStatus;
import com.chainlance.entity.enums.ProposalStatus;
import com.chainlance.entity.enums.Role;
import com.chainlance.repository.NegotiationOfferRepository;
import com.chainlance.repository.ProjectRepository;
import com.chainlance.repository.ProposalRepository;
import com.chainlance.repository.UserRepository;
import com.chainlance.web.ApiException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Proposal rules. Status changes go through {@link ProposalTransitions}. */
@Service
public class ProposalService {

    private final ProposalRepository proposals;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final NegotiationOfferRepository offers;
    private final ProposalAccess access;
    private final ProjectAccess projectAccess;
    private final ProposalWorkflow workflow;

    public ProposalService(ProposalRepository proposals, ProjectRepository projects, UserRepository users,
                           NegotiationOfferRepository offers, ProposalAccess access, ProjectAccess projectAccess,
                           ProposalWorkflow workflow) {
        this.proposals = proposals;
        this.projects = projects;
        this.users = users;
        this.offers = offers;
        this.access = access;
        this.projectAccess = projectAccess;
        this.workflow = workflow;
    }

    /** Creates the proposal and its first offer (the freelancer's price) together or not at all. */
    @Transactional
    public ProposalResponse submit(Long projectId, Long freelancerId, SubmitProposalRequest request) {
        User freelancer = users.findById(freelancerId).orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"));
        if (freelancer.getRole() != Role.FREELANCER) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only freelancers can submit proposals");
        }
        Project project = projects.findById(projectId).orElseThrow(ProjectAccess::notFound);
        if (project.getStatus() == ProjectStatus.DRAFT) {
            throw ProjectAccess.notFound(); // drafts are private
        }
        if (project.getStatus() != ProjectStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_NOT_OPEN",
                    "This project is " + project.getStatus() + " and no longer takes proposals");
        }

        BigDecimal price = PriceRules.normalize(request.priceInr());
        PriceRules.requireWithinBudget(price, project.getBudgetInr());
        int days = PriceRules.requireDuration(request.durationDays());

        if (proposals.existsByProjectIdAndFreelancerIdAndStatusIn(projectId, freelancerId, ProposalTransitions.ACTIVE)) {
            throw duplicate();
        }

        Proposal proposal = new Proposal();
        proposal.setProject(project);
        proposal.setFreelancer(freelancer); // from the login, never from the request
        proposal.setMessage(request.message().trim());
        proposal.setPriceInr(price);
        proposal.setDurationDays(days);
        proposal.setPortfolioLinks(PriceRules.blankToNull(request.portfolioLinks()));
        proposal.setStatus(ProposalStatus.SUBMITTED);
        try {
            proposal = proposals.saveAndFlush(proposal); // the unique key catches a race the check above cannot
        } catch (DataIntegrityViolationException e) {
            throw duplicate();
        }

        NegotiationOffer first = new NegotiationOffer();
        first.setProposal(proposal);
        first.setOfferedBy(freelancer);
        first.setPriceInr(price);
        first.setDurationDays(days);
        first = offers.save(first);
        return ProposalResponse.from(proposal, List.of(OfferResponse.from(first)));
    }

    @Transactional(readOnly = true)
    public List<ProposalResponse> listMine(Long freelancerId) {
        return proposals.findByFreelancerIdOrderByCreatedAtDesc(freelancerId).stream()
                .map(p -> ProposalResponse.from(p, null)).toList();
    }

    /** Only the project's own client. */
    @Transactional(readOnly = true)
    public List<ProposalResponse> listForProject(Long projectId, Long clientId) {
        projectAccess.loadOwned(projectId, clientId);
        return proposals.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .map(p -> ProposalResponse.from(p, null)).toList();
    }

    /** The freelancer who wrote it or the client who owns the project. Includes the negotiation history. */
    @Transactional(readOnly = true)
    public ProposalResponse get(Long proposalId, Long userId) {
        Proposal proposal = access.loadForParticipant(proposalId, userId, false);
        List<OfferResponse> history = offers.findByProposalIdOrderByCreatedAtAscIdAsc(proposalId).stream()
                .map(OfferResponse::from).toList();
        return ProposalResponse.from(proposal, history);
    }

    @Transactional
    public ProposalResponse shortlist(Long proposalId, Long clientId) {
        Proposal proposal = access.loadForClient(proposalId, clientId, true);
        ProposalTransitions.require(proposal.getStatus(), ProposalStatus.SHORTLISTED);
        proposal.setStatus(ProposalStatus.SHORTLISTED);
        return ProposalResponse.from(proposals.saveAndFlush(proposal), null);
    }

    @Transactional
    public ProposalResponse reject(Long proposalId, Long clientId) {
        Proposal proposal = access.loadForClient(proposalId, clientId, true);
        ProposalTransitions.require(proposal.getStatus(), ProposalStatus.REJECTED);
        proposal.setStatus(ProposalStatus.REJECTED);
        proposals.saveAndFlush(proposal);
        workflow.closePendingOffers(proposalId);
        return ProposalResponse.from(proposal, null);
    }

    @Transactional
    public ProposalResponse withdraw(Long proposalId, Long freelancerId) {
        Proposal proposal = access.loadForFreelancer(proposalId, freelancerId, true);
        ProposalTransitions.require(proposal.getStatus(), ProposalStatus.WITHDRAWN);
        proposal.setStatus(ProposalStatus.WITHDRAWN);
        proposals.saveAndFlush(proposal);
        workflow.closePendingOffers(proposalId);
        return ProposalResponse.from(proposal, null);
    }

    private static ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, "DUPLICATE_PROPOSAL",
                "You already have an active proposal for this project. Withdraw it first to submit a new one");
    }
}
