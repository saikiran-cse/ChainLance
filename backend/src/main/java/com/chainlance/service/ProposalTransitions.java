package com.chainlance.service;

import com.chainlance.entity.enums.ProposalStatus;
import com.chainlance.web.ApiException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * The only allowed proposal status changes. The frontend never sends a status: it calls an action
 * (shortlist, reject, withdraw, accept) and the service asks this class whether the move is legal.
 */
public final class ProposalTransitions {

    /** Proposals that still count as "the freelancer's live proposal" (matches uq_proposal_one_active in V2). */
    public static final Set<ProposalStatus> ACTIVE =
            EnumSet.of(ProposalStatus.SUBMITTED, ProposalStatus.SHORTLISTED, ProposalStatus.ACCEPTED);

    /** Proposals that can still be negotiated or accepted. */
    public static final Set<ProposalStatus> OPEN_FOR_NEGOTIATION =
            EnumSet.of(ProposalStatus.SUBMITTED, ProposalStatus.SHORTLISTED);

    private static final Map<ProposalStatus, Set<ProposalStatus>> ALLOWED = new EnumMap<>(ProposalStatus.class);

    static {
        ALLOWED.put(ProposalStatus.SUBMITTED,
                EnumSet.of(ProposalStatus.SHORTLISTED, ProposalStatus.ACCEPTED, ProposalStatus.REJECTED, ProposalStatus.WITHDRAWN));
        ALLOWED.put(ProposalStatus.SHORTLISTED,
                EnumSet.of(ProposalStatus.ACCEPTED, ProposalStatus.REJECTED, ProposalStatus.WITHDRAWN));
        ALLOWED.put(ProposalStatus.ACCEPTED, EnumSet.noneOf(ProposalStatus.class));
        ALLOWED.put(ProposalStatus.REJECTED, EnumSet.noneOf(ProposalStatus.class));
        ALLOWED.put(ProposalStatus.WITHDRAWN, EnumSet.noneOf(ProposalStatus.class));
    }

    private ProposalTransitions() {
    }

    public static boolean canMove(ProposalStatus from, ProposalStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public static void require(ProposalStatus from, ProposalStatus to) {
        if (!canMove(from, to)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                    "A proposal that is " + from + " cannot become " + to);
        }
    }
}
