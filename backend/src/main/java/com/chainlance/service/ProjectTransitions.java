package com.chainlance.service;

import com.chainlance.entity.enums.ProjectStatus;
import com.chainlance.web.ApiException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * The only allowed project status changes in Stage 4.
 * OPEN to IN_PROGRESS happens only inside agreement creation. IN_PROGRESS has no exits yet: completing or
 * cancelling work that is under agreement belongs to the milestone and escrow stages.
 */
public final class ProjectTransitions {

    private static final Map<ProjectStatus, Set<ProjectStatus>> ALLOWED = new EnumMap<>(ProjectStatus.class);

    static {
        ALLOWED.put(ProjectStatus.DRAFT, EnumSet.of(ProjectStatus.OPEN, ProjectStatus.CANCELLED));
        ALLOWED.put(ProjectStatus.OPEN,
                EnumSet.of(ProjectStatus.CLOSED, ProjectStatus.CANCELLED, ProjectStatus.IN_PROGRESS));
        ALLOWED.put(ProjectStatus.IN_PROGRESS, EnumSet.noneOf(ProjectStatus.class));
        ALLOWED.put(ProjectStatus.COMPLETED, EnumSet.noneOf(ProjectStatus.class));
        ALLOWED.put(ProjectStatus.CLOSED, EnumSet.noneOf(ProjectStatus.class));
        ALLOWED.put(ProjectStatus.CANCELLED, EnumSet.noneOf(ProjectStatus.class));
    }

    private ProjectTransitions() {
    }

    public static boolean canMove(ProjectStatus from, ProjectStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public static void require(ProjectStatus from, ProjectStatus to) {
        if (!canMove(from, to)) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATUS_TRANSITION",
                    "A project that is " + from + " cannot become " + to);
        }
    }
}
