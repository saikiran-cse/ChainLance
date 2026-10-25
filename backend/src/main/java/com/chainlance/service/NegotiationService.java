package com.chainlance.service;

import com.chainlance.dto.OfferRequest;
import com.chainlance.dto.OfferResponse;
import com.chainlance.entity.NegotiationOffer;
import com.chainlance.entity.Proposal;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.OfferStatus;
import com.chainlance.entity.enums.ProjectStatus;
import com.chainlance.repository.NegotiationOfferRepository;
import com.chainlance.repository.UserRepository;
import com.chainlance.web.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Price negotiation. The freelancer's proposal is offer 1. At any moment at most one offer is PENDING and only
 * the OTHER party can answer it: accept (terms agreed) or counter (a new PENDING offer). Every change locks the
 * proposal row first, so two quick clicks cannot both succeed.
 */
@Service
public class NegotiationService {

    private final ProposalAccess access;
    private final NegotiationOfferRepository offers;
    private final UserRepository users;
    private final Clock clock;

    public NegotiationService(ProposalAccess access, NegotiationOfferRepository offers, UserRepository users, Clock clock) {
        this.access = access;
        this.offers = offers;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OfferResponse> history(Long proposalId, Long userId) {
        access.loadForParticipant(proposalId, userId, false);
        return offers.findByProposalIdOrderByCreatedAtAscIdAsc(proposalId).stream().map(OfferResponse::from).toList();
    }

    /** A fresh offer, allowed only when nothing is waiting for an answer (for example after a withdrawal). */
    @Transactional
    public OfferResponse makeOffer(Long proposalId, Long userId, OfferRequest request) {
        Proposal proposal = access.loadForParticipant(proposalId, userId, true);
        requireNegotiable(proposal);
        if (offers.existsByProposalIdAndStatus(proposalId, OfferStatus.PENDING)) {
            throw new ApiException(HttpStatus.CONFLICT, "OFFER_PENDING",
                    "An offer is already waiting. Accept or counter it, or withdraw your own offer first");
        }
        return OfferResponse.from(createOffer(proposal, userId, request));
    }

    /** Answers the pending offer with a new one. The old offer becomes COUNTERED. */
    @Transactional
    public OfferResponse counter(Long proposalId, Long offerId, Long userId, OfferRequest request) {
        Proposal proposal = access.loadForParticipant(proposalId, userId, true);
        requireNegotiable(proposal);
        NegotiationOffer pending = answerablePendingOffer(proposal, offerId, userId);
        BigDecimal price = PriceRules.normalize(request.priceInr());
        PriceRules.requireWithinBudget(price, proposal.getProject().getBudgetInr());
        PriceRules.requireDuration(request.durationDays());

        markAnswered(pending, OfferStatus.COUNTERED, userId);
        offers.saveAndFlush(pending); // must reach MySQL BEFORE the new PENDING row (one pending per proposal)
        return OfferResponse.from(createOffer(proposal, userId, request));
    }

    /** Accepts the pending offer. The terms are now agreed. The client then accepts the proposal to create the agreement. */
    @Transactional
    public OfferResponse accept(Long proposalId, Long offerId, Long userId) {
        Proposal proposal = access.loadForParticipant(proposalId, userId, true);
        requireNegotiable(proposal);
        NegotiationOffer pending = answerablePendingOffer(proposal, offerId, userId);
        markAnswered(pending, OfferStatus.ACCEPTED, userId);
        return OfferResponse.from(offers.saveAndFlush(pending));
    }

    /** Only the author can take back their own pending offer. */
    @Transactional
    public OfferResponse withdraw(Long proposalId, Long offerId, Long userId) {
        Proposal proposal = access.loadForParticipant(proposalId, userId, true);
        NegotiationOffer offer = findOffer(proposal, offerId);
        if (!offer.getOfferedBy().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the author can withdraw an offer");
        }
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw notPending();
        }
        offer.setStatus(OfferStatus.WITHDRAWN);
        offer.setRespondedAt(Instant.now(clock));
        return OfferResponse.from(offers.saveAndFlush(offer));
    }

    // ------------------------------------------------------------------ helpers

    /** Negotiation is possible only while the proposal is live, the project is OPEN and nothing was accepted yet. */
    private void requireNegotiable(Proposal proposal) {
        if (!ProposalTransitions.OPEN_FOR_NEGOTIATION.contains(proposal.getStatus())) {
            throw new ApiException(HttpStatus.CONFLICT, "PROPOSAL_NOT_ACTIVE",
                    "This proposal is " + proposal.getStatus() + " and can no longer be negotiated");
        }
        if (proposal.getProject().getStatus() != ProjectStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_NOT_OPEN", "The project is no longer open");
        }
        if (offers.existsByProposalIdAndStatus(proposal.getId(), OfferStatus.ACCEPTED)) {
            throw new ApiException(HttpStatus.CONFLICT, "NEGOTIATION_CLOSED",
                    "The terms are already agreed. The client can now accept the proposal");
        }
    }

    private NegotiationOffer findOffer(Proposal proposal, Long offerId) {
        NegotiationOffer offer = offers.findById(offerId).orElseThrow(NegotiationService::offerNotFound);
        if (!offer.getProposal().getId().equals(proposal.getId())) {
            throw offerNotFound(); // an offer id from another proposal is treated as unknown
        }
        return offer;
    }

    /** The offer must be PENDING and written by the OTHER participant. */
    private NegotiationOffer answerablePendingOffer(Proposal proposal, Long offerId, Long userId) {
        NegotiationOffer offer = findOffer(proposal, offerId);
        if (offer.getStatus() != OfferStatus.PENDING) {
            throw notPending();
        }
        if (offer.getOfferedBy().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CANNOT_RESPOND_TO_OWN_OFFER",
                    "Only the other party can answer your offer");
        }
        return offer;
    }

    private void markAnswered(NegotiationOffer offer, OfferStatus status, Long userId) {
        offer.setStatus(status);
        offer.setRespondedBy(users.getReferenceById(userId));
        offer.setRespondedAt(Instant.now(clock));
    }

    private NegotiationOffer createOffer(Proposal proposal, Long userId, OfferRequest request) {
        BigDecimal price = PriceRules.normalize(request.priceInr());
        PriceRules.requireWithinBudget(price, proposal.getProject().getBudgetInr());
        int days = PriceRules.requireDuration(request.durationDays());
        User author = users.findById(userId).orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"));

        NegotiationOffer offer = new NegotiationOffer();
        offer.setProposal(proposal);
        offer.setOfferedBy(author); // from the login, never from the request
        offer.setPriceInr(price);
        offer.setDurationDays(days);
        offer.setNote(PriceRules.blankToNull(request.note()));
        return offers.save(offer);
    }

    private static ApiException offerNotFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "OFFER_NOT_FOUND", "Offer not found");
    }

    private static ApiException notPending() {
        return new ApiException(HttpStatus.CONFLICT, "OFFER_NOT_PENDING", "This offer has already been answered or withdrawn");
    }
}
