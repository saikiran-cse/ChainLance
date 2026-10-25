package com.chainlance.controller;

import com.chainlance.dto.AdminDecisionRequest;
import com.chainlance.dto.DisputeDetailResponse;
import com.chainlance.dto.DisputeSummaryResponse;
import com.chainlance.dto.PageResponse;
import com.chainlance.dto.ResolveDisputeRequest;
import com.chainlance.entity.enums.DisputeStatus;
import com.chainlance.security.AppUserDetails;
import com.chainlance.service.DisputeService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/disputes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDisputeController {

    private final DisputeService disputeService;

    public AdminDisputeController(DisputeService disputeService) {
        this.disputeService = disputeService;
    }

    @GetMapping
    public PageResponse<DisputeSummaryResponse> listDisputes(
            @RequestParam(required = false) List<DisputeStatus> status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DisputeSummaryResponse> result = disputeService.adminListDisputes(status, pageable);
        return PageResponse.of(result);
    }

    @PostMapping("/{id}/decision")
    public DisputeDetailResponse recordDecision(@AuthenticationPrincipal AppUserDetails current,
                                                @PathVariable Long id,
                                                @Valid @RequestBody AdminDecisionRequest request) {
        return disputeService.adminRecordDecision(id, current.getId(), request);
    }

    @PostMapping("/{id}/resolution-tx")
    public DisputeDetailResponse submitResolutionTx(@AuthenticationPrincipal AppUserDetails current,
                                                    @PathVariable Long id,
                                                    @Valid @RequestBody ResolveDisputeRequest request) {
        return disputeService.adminSubmitResolutionTx(id, current.getId(), request);
    }
}
