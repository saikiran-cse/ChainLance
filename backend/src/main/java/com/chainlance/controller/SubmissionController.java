package com.chainlance.controller;

import com.chainlance.dto.ApproveSubmissionRequest;
import com.chainlance.dto.RevisionRequest;
import com.chainlance.dto.SubmissionResponse;
import com.chainlance.dto.SubmitWorkRequest;
import com.chainlance.security.AppUserDetails;
import com.chainlance.service.SubmissionService;
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
public class SubmissionController {

    private final SubmissionService submissionService;

    public SubmissionController(SubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @PostMapping("/milestones/{id}/submissions")
    @PreAuthorize("hasRole('FREELANCER')")
    public SubmissionResponse submitWork(@AuthenticationPrincipal AppUserDetails current,
                                         @PathVariable Long id,
                                         @Valid @RequestBody SubmitWorkRequest request) {
        return submissionService.submitWork(id, current.getId(), request);
    }

    @GetMapping("/milestones/{id}/submissions")
    public List<SubmissionResponse> getSubmissions(@AuthenticationPrincipal AppUserDetails current,
                                                   @PathVariable Long id) {
        return submissionService.getSubmissions(id, current.getId());
    }

    @PostMapping("/submissions/{id}/approve")
    @PreAuthorize("hasRole('CLIENT')")
    public SubmissionResponse approveSubmission(@AuthenticationPrincipal AppUserDetails current,
                                                @PathVariable Long id,
                                                @RequestBody(required = false) ApproveSubmissionRequest request) {
        return submissionService.approveSubmission(id, current.getId(), request);
    }

    @PostMapping("/submissions/{id}/request-revision")
    @PreAuthorize("hasRole('CLIENT')")
    public SubmissionResponse requestRevision(@AuthenticationPrincipal AppUserDetails current,
                                              @PathVariable Long id,
                                              @Valid @RequestBody RevisionRequest request) {
        return submissionService.requestRevision(id, current.getId(), request);
    }
}
