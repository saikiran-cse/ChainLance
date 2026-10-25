package com.chainlance.service;

import com.chainlance.entity.Proposal;
import com.chainlance.entity.enums.OfferStatus;
import com.chainlance.entity.enums.ProposalStatus;
import com.chainlance.repository.NegotiationOfferRepository;
import com.chainlance.repository.ProposalRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Clean-up steps shared by several services. Always called inside the caller's transaction. */
@Component
public class ProposalWorkflow {

    private final ProposalRepository proposals;
    private final NegotiationOfferRepository offers;
    private final Clock clock;

    public ProposalWorkflow(ProposalRepository proposals, NegotiationOfferRepository offers, Clock clock) {
        this.proposals = proposals;
        this.offers = offers;
        this.clock = clock;
    }

    /** A proposal that ends must not keep a PENDING offer waiting for an answer. */
    public void closePendingOffers(Long proposalId) {
        offers.findFirstByProposalIdAndStatus(proposalId, OfferStatus.PENDING).ifPresent(offer -> {
            offer.setStatus(OfferStatus.CLOSED);
            offer.setRespondedAt(Instant.now(clock));
            offers.saveAndFlush(offer);
        });
    }

    /** Rejects every still-open proposal of a project, except one (the accepted one), and closes their offers. */
    public void rejectOpenProposals(Long projectId, Long exceptProposalId) {
        for (Proposal other : proposals.findByProjectIdAndStatusIn(projectId, ProposalTransitions.OPEN_FOR_NEGOTIATION)) {
            if (other.getId().equals(exceptProposalId)) {
                continue;
            }
            other.setStatus(ProposalStatus.REJECTED);
            proposals.saveAndFlush(other);
            closePendingOffers(other.getId());
        }
    }
}
