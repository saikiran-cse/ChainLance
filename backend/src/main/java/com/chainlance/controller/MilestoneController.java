package com.chainlance.controller;

import com.chainlance.dto.MilestoneResponse;
import com.chainlance.security.AppUserDetails;
import com.chainlance.service.MilestoneService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MilestoneController {

    private final MilestoneService milestoneService;

    public MilestoneController(MilestoneService milestoneService) {
        this.milestoneService = milestoneService;
    }

    /**
     * Client only: generates milestone allocations for an agreement.
     * Idempotent: returns existing milestones if already generated.
     */
    @PostMapping("/agreements/{id}/milestones/generate")
    @PreAuthorize("hasRole('CLIENT')")
    public List<MilestoneResponse> generateMilestones(@AuthenticationPrincipal AppUserDetails current,
                                                     @PathVariable Long id) {
        return milestoneService.generateMilestones(id, current.getId());
    }

    /** Client or Freelancer: list milestones for an agreement. */
    @GetMapping("/agreements/{id}/milestones")
    public List<MilestoneResponse> getMilestones(@AuthenticationPrincipal AppUserDetails current,
                                                @PathVariable Long id) {
        return milestoneService.getMilestones(id, current.getId());
    }

    /** Client or Freelancer: get a single milestone by id. */
    @GetMapping("/milestones/{id}")
    public MilestoneResponse getMilestone(@AuthenticationPrincipal AppUserDetails current,
                                          @PathVariable Long id) {
        return milestoneService.getMilestone(id, current.getId());
    }
}
