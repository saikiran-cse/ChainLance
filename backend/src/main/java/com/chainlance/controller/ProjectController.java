package com.chainlance.controller;

import com.chainlance.dto.PageResponse;
import com.chainlance.dto.ProjectRequest;
import com.chainlance.dto.ProjectResponse;
import com.chainlance.security.AppUserDetails;
import com.chainlance.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    /** Client only. The new project starts as DRAFT. */
    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@AuthenticationPrincipal AppUserDetails current, @Valid @RequestBody ProjectRequest request) {
        return projectService.create(current.getId(), request);
    }

    /** Anyone logged in: OPEN projects, newest first. Optional ?category=...&page=0&size=20 (size is capped at 50). */
    @GetMapping
    public PageResponse<ProjectResponse> listOpen(@RequestParam(required = false) String category,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return projectService.listOpen(category, page, size);
    }

    /** Client only: my own projects in every status. */
    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLIENT')")
    public List<ProjectResponse> mine(@AuthenticationPrincipal AppUserDetails current) {
        return projectService.listMine(current.getId());
    }

    @GetMapping("/{id}")
    public ProjectResponse get(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long id) {
        return projectService.get(id, current.getId(), current.getRole());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CLIENT')")
    public ProjectResponse update(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long id,
                                  @Valid @RequestBody ProjectRequest request) {
        return projectService.update(id, current.getId(), request);
    }

    /** DRAFT to OPEN. */
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('CLIENT')")
    public ProjectResponse publish(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long id) {
        return projectService.publish(id, current.getId());
    }

    /** OPEN to CLOSED. */
    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('CLIENT')")
    public ProjectResponse close(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long id) {
        return projectService.close(id, current.getId());
    }

    /** DRAFT or OPEN to CANCELLED. */
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CLIENT')")
    public ProjectResponse cancel(@AuthenticationPrincipal AppUserDetails current, @PathVariable Long id) {
        return projectService.cancel(id, current.getId());
    }
}
