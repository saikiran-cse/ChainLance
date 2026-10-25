package com.chainlance.service;

import com.chainlance.entity.Project;
import com.chainlance.entity.enums.ProjectStatus;
import com.chainlance.repository.ProjectRepository;
import com.chainlance.web.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Ownership check for projects. Must run inside a transaction (it reads the lazy client). */
@Component
public class ProjectAccess {

    private final ProjectRepository projects;

    public ProjectAccess(ProjectRepository projects) {
        this.projects = projects;
    }

    /**
     * Loads a project the caller must own. A project that other people can see (OPEN) answers 403 to a non-owner;
     * a private one (draft, closed...) answers 404 so its existence is not revealed.
     */
    public Project loadOwned(Long projectId, Long clientId) {
        Project project = projects.findById(projectId).orElseThrow(ProjectAccess::notFound);
        if (!project.getClient().getId().equals(clientId)) {
            if (project.getStatus() == ProjectStatus.OPEN) {
                throw new ApiException(HttpStatus.FORBIDDEN, "NOT_PROJECT_OWNER", "You do not own this project");
            }
            throw notFound();
        }
        return project;
    }

    static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "Project not found");
    }
}
