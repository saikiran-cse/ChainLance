package com.chainlance.controller;

import com.chainlance.dto.ProposalResponse;
import com.chainlance.dto.SubmitProposalRequest;
import com.chainlance.security.AppUserDetails;
import com.chainlance.service.ProposalService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ProposalController {

    private final ProposalService proposalService;

    public ProposalController(ProposalService proposalService) {
        this.proposalService = proposalService;
    }

    /** Freelancer only. */
    @PostMapping("/projects/{projectId}/proposals")
    @PreAuthorize("hasRole('FREELANCER')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProposalResponse submit(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long projectId,
                                   @Valid @RequestBody SubmitProposalRequest request) {
        return proposalService.submit(projectId, current.getId(), request);
    }

    /** Client only, and only for their own project. */
    @GetMapping("/projects/{projectId}/proposals")
    @PreAuthorize("hasRole('CLIENT')")
    public List<ProposalResponse> forProject(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long projectId) {
        return proposalService.listForProject(projectId, current.getId());
    }

    /** Freelancer only: the proposals I submitted. */
    @GetMapping("/proposals/my")
    @PreAuthorize("hasRole('FREELANCER')")
    public List<ProposalResponse> mine(@AuthenticationPrincipal AppUserDetails current) {
        return proposalService.listMine(current.getId());
    }

    /** The freelancer or the project's client. Includes the negotiation history. */
    @GetMapping("/proposals/{id}")
    public ProposalResponse get(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long id) {
        return proposalService.get(id, current.getId());
    }

    @PostMapping("/proposals/{id}/shortlist")
    @PreAuthorize("hasRole('CLIENT')")
    public ProposalResponse shortlist(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long id) {
        return proposalService.shortlist(id, current.getId());
    }

    @PostMapping("/proposals/{id}/reject")
    @PreAuthorize("hasRole('CLIENT')")
    public ProposalResponse reject(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long id) {
        return proposalService.reject(id, current.getId());
    }

    @PostMapping("/proposals/{id}/withdraw")
    @PreAuthorize("hasRole('FREELANCER')")
    public ProposalResponse withdraw(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long id) {
        return proposalService.withdraw(id, current.getId());
    }
}
