package com.chainlance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chainlance.entity.enums.ProjectStatus;
import com.chainlance.entity.enums.ProposalStatus;
import com.chainlance.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** No Spring, no database: the status rules are plain Java. */
class TransitionRulesTest {

    @Test
    void liveProposalsCanBeShortlistedAcceptedRejectedOrWithdrawn() {
        assertTrue(ProposalTransitions.canMove(ProposalStatus.SUBMITTED, ProposalStatus.SHORTLISTED));
        assertTrue(ProposalTransitions.canMove(ProposalStatus.SUBMITTED, ProposalStatus.ACCEPTED));
        assertTrue(ProposalTransitions.canMove(ProposalStatus.SHORTLISTED, ProposalStatus.ACCEPTED));
        assertTrue(ProposalTransitions.canMove(ProposalStatus.SUBMITTED, ProposalStatus.REJECTED));
        assertTrue(ProposalTransitions.canMove(ProposalStatus.SHORTLISTED, ProposalStatus.WITHDRAWN));
    }

    @Test
    void finishedProposalsCannotMoveAnywhere() {
        for (ProposalStatus finished : new ProposalStatus[]{ProposalStatus.ACCEPTED, ProposalStatus.REJECTED, ProposalStatus.WITHDRAWN}) {
            for (ProposalStatus target : ProposalStatus.values()) {
                assertFalse(ProposalTransitions.canMove(finished, target), finished + " -> " + target);
            }
        }
    }

    @Test
    void shortlistedCannotGoBackToSubmitted() {
        assertFalse(ProposalTransitions.canMove(ProposalStatus.SHORTLISTED, ProposalStatus.SUBMITTED));
    }

    @Test
    void illegalProposalMoveIsA409WithAClearCode() {
        ApiException e = assertThrows(ApiException.class,
                () -> ProposalTransitions.require(ProposalStatus.REJECTED, ProposalStatus.ACCEPTED));
        assertEquals(HttpStatus.CONFLICT, e.getStatus());
        assertEquals("INVALID_STATUS_TRANSITION", e.getCode());
    }

    @Test
    void activeProposalStatusesMatchTheDatabaseUniqueKey() {
        // V2: active_key is non-NULL for exactly SUBMITTED, SHORTLISTED and ACCEPTED
        assertEquals(3, ProposalTransitions.ACTIVE.size());
        assertTrue(ProposalTransitions.ACTIVE.contains(ProposalStatus.ACCEPTED));
        assertFalse(ProposalTransitions.ACTIVE.contains(ProposalStatus.REJECTED));
        assertFalse(ProposalTransitions.ACTIVE.contains(ProposalStatus.WITHDRAWN));
    }

    @Test
    void projectLifecycleIsDraftOpenThenClosedCancelledOrInProgress() {
        assertTrue(ProjectTransitions.canMove(ProjectStatus.DRAFT, ProjectStatus.OPEN));
        assertTrue(ProjectTransitions.canMove(ProjectStatus.DRAFT, ProjectStatus.CANCELLED));
        assertTrue(ProjectTransitions.canMove(ProjectStatus.OPEN, ProjectStatus.CLOSED));
        assertTrue(ProjectTransitions.canMove(ProjectStatus.OPEN, ProjectStatus.CANCELLED));
        assertTrue(ProjectTransitions.canMove(ProjectStatus.OPEN, ProjectStatus.IN_PROGRESS));
    }

    @Test
    void projectCannotSkipOrReopen() {
        assertFalse(ProjectTransitions.canMove(ProjectStatus.DRAFT, ProjectStatus.CLOSED));
        assertFalse(ProjectTransitions.canMove(ProjectStatus.DRAFT, ProjectStatus.IN_PROGRESS));
        assertFalse(ProjectTransitions.canMove(ProjectStatus.CLOSED, ProjectStatus.OPEN));
        assertFalse(ProjectTransitions.canMove(ProjectStatus.CANCELLED, ProjectStatus.OPEN));
    }

    @Test
    void aProjectUnderAgreementCannotBeClosedOrCancelledYet() {
        assertFalse(ProjectTransitions.canMove(ProjectStatus.IN_PROGRESS, ProjectStatus.CLOSED));
        assertFalse(ProjectTransitions.canMove(ProjectStatus.IN_PROGRESS, ProjectStatus.CANCELLED));
        assertThrows(ApiException.class,
                () -> ProjectTransitions.require(ProjectStatus.IN_PROGRESS, ProjectStatus.CANCELLED));
    }
}
