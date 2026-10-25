package com.chainlance.controller;

import com.chainlance.dto.DisputeDetailResponse;
import com.chainlance.dto.DisputeReplyRequest;
import com.chainlance.dto.DisputeReplyResponse;
import com.chainlance.dto.OpenDisputeRequest;
import com.chainlance.entity.enums.Role;
import com.chainlance.security.AppUserDetails;
import com.chainlance.service.DisputeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DisputeController {

    private final DisputeService disputeService;

    public DisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @PostMapping("/milestones/{id}/disputes")
    public DisputeDetailResponse openDispute(@AuthenticationPrincipal AppUserDetails current,
                                             @PathVariable Long id,
                                             @Valid @RequestBody OpenDisputeRequest request) {
        return disputeService.openDispute(id, current.getId(), request);
    }

    @GetMapping("/milestones/{id}/disputes")
    public List<DisputeDetailResponse> getDisputesForMilestone(@AuthenticationPrincipal AppUserDetails current,
                                                              @PathVariable Long id) {
        return disputeService.getDisputesForMilestone(id, current.getId());
    }

    @GetMapping("/disputes/{id}")
    public DisputeDetailResponse getDispute(@AuthenticationPrincipal AppUserDetails current,
                                            @PathVariable Long id) {
        boolean isAdmin = current.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return disputeService.getDispute(id, current.getId(), isAdmin);
    }

    @PostMapping("/disputes/{id}/responses")
    public DisputeReplyResponse replyToDispute(@AuthenticationPrincipal AppUserDetails current,
                                               @PathVariable Long id,
                                               @Valid @RequestBody DisputeReplyRequest request) {
        boolean isAdmin = current.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return disputeService.replyToDispute(id, current.getId(), isAdmin, request);
    }
}
