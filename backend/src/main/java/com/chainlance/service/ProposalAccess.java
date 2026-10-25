package com.chainlance.service;

import com.chainlance.entity.Proposal;
import com.chainlance.repository.ProposalRepository;
import com.chainlance.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Who may touch a proposal. Anyone who is not its freelancer or its project's client gets 404 (not 403),
 * so people cannot discover which proposal ids exist. Must run inside a transaction.
 */
@Component
public class ProposalAccess {

    private final ProposalRepository proposals;

    public ProposalAccess(ProposalRepository proposals) {
        this.proposals = proposals;
    }

    /** forUpdate = true takes a row lock, so concurrent changes to the same proposal run one at a time. */
    public Proposal loadForParticipant(Long proposalId, Long userId, boolean forUpdate) {
        Proposal proposal = (forUpdate ? proposals.findByIdForUpdate(proposalId) : proposals.findById(proposalId))
                .orElseThrow(ProposalAccess::notFound);
        if (!isClient(proposal, userId) && !isFreelancer(proposal, userId)) {
            throw notFound();
        }
        return proposal;
    }

    public Proposal loadForClient(Long proposalId, Long userId, boolean forUpdate) {
        Proposal proposal = loadForParticipant(proposalId, userId, forUpdate);
        if (!isClient(proposal, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the project's client can do this");
        }
        return proposal;
    }

    public Proposal loadForFreelancer(Long proposalId, Long userId, boolean forUpdate) {
        Proposal proposal = loadForParticipant(proposalId, userId, forUpdate);
        if (!isFreelancer(proposal, userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the proposing freelancer can do this");
        }
        return proposal;
    }

    public static boolean isClient(Proposal proposal, Long userId) {
        return proposal.getProject().getClient().getId().equals(userId);
    }

    public static boolean isFreelancer(Proposal proposal, Long userId) {
        return proposal.getFreelancer().getId().equals(userId);
    }

    static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROPOSAL_NOT_FOUND", "Proposal not found");
    }
}
