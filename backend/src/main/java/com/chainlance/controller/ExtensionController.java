package com.chainlance.controller;

import com.chainlance.dto.CreateExtensionRequest;
import com.chainlance.dto.DecisionRequest;
import com.chainlance.dto.ExtensionResponse;
import com.chainlance.security.AppUserDetails;
import com.chainlance.service.ExtensionService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ExtensionController {

    private final ExtensionService extensionService;

    public ExtensionController(ExtensionService extensionService) {
        this.extensionService = extensionService;
    }

    @PostMapping("/milestones/{id}/extensions")
    @PreAuthorize("hasRole('FREELANCER')")
    public ExtensionResponse requestExtension(@AuthenticationPrincipal AppUserDetails current,
                                              @PathVariable Long id,
                                              @Valid @RequestBody CreateExtensionRequest request) {
        return extensionService.requestExtension(id, current.getId(), request);
    }

    @GetMapping("/milestones/{id}/extensions")
    public List<ExtensionResponse> getExtensions(@AuthenticationPrincipal AppUserDetails current,
                                                 @PathVariable Long id) {
        return extensionService.getExtensions(id, current.getId());
    }

    @PostMapping("/extensions/{id}/approve")
    @PreAuthorize("hasRole('CLIENT')")
    public ExtensionResponse approveExtension(@AuthenticationPrincipal AppUserDetails current,
                                              @PathVariable Long id,
                                              @RequestBody(required = false) DecisionRequest request) {
        return extensionService.approveExtension(id, current.getId(), request);
    }

    @PostMapping("/extensions/{id}/reject")
    @PreAuthorize("hasRole('CLIENT')")
    public ExtensionResponse rejectExtension(@AuthenticationPrincipal AppUserDetails current,
                                             @PathVariable Long id,
                                             @RequestBody(required = false) DecisionRequest request) {
        return extensionService.rejectExtension(id, current.getId(), request);
    }
}
