package com.chainlance.service;

import com.chainlance.dto.PageResponse;
import com.chainlance.dto.ProjectRequest;
import com.chainlance.dto.ProjectResponse;
import com.chainlance.entity.Project;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.ProjectStatus;
import com.chainlance.entity.enums.Role;
import com.chainlance.repository.ProjectRepository;
import com.chainlance.repository.ProposalRepository;
import com.chainlance.repository.UserRepository;
import com.chainlance.web.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Project rules: who can create or change a project and which status moves are legal. */
@Service
public class ProjectService {

    static final int MAX_PAGE_SIZE = 50;

    private final ProjectRepository projects;
    private final UserRepository users;
    private final ProposalRepository proposals;
    private final ProjectAccess access;
    private final ProposalWorkflow workflow;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, UserRepository users, ProposalRepository proposals,
                          ProjectAccess access, ProposalWorkflow workflow, Clock clock) {
        this.projects = projects;
        this.users = users;
        this.proposals = proposals;
        this.access = access;
        this.workflow = workflow;
        this.clock = clock;
    }

    @Transactional
    public ProjectResponse create(Long clientId, ProjectRequest request) {
        User client = users.findById(clientId).orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required"));
        if (client.getRole() != Role.CLIENT) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only clients can create projects");
        }
        requireFutureDeadline(request.deadline());

        Project project = new Project();
        project.setClient(client); // from the login, never from the request
        apply(project, request);
        project.setStatus(ProjectStatus.DRAFT);
        return ProjectResponse.from(projects.save(project));
    }

    /** Everyone logged in can browse OPEN projects. */
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> listOpen(String category, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        String wanted = PriceRules.blankToNull(category);
        Page<Project> result = wanted == null
                ? projects.findByStatus(ProjectStatus.OPEN, pageable)
                : projects.findByStatusAndCategoryIgnoreCase(ProjectStatus.OPEN, wanted, pageable);
        return PageResponse.of(result.map(ProjectResponse::from));
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listMine(Long clientId) {
        return projects.findByClientIdOrderByCreatedAtDesc(clientId).stream().map(ProjectResponse::from).toList();
    }

    /**
     * OPEN projects are public. Any other project is visible only to its client, an admin, or a freelancer
     * who has a proposal on it; everyone else gets 404.
     */
    @Transactional(readOnly = true)
    public ProjectResponse get(Long projectId, Long userId, Role role) {
        Project project = projects.findById(projectId).orElseThrow(ProjectAccess::notFound);
        boolean visible = project.getStatus() == ProjectStatus.OPEN
                || role == Role.ADMIN
                || project.getClient().getId().equals(userId)
                || proposals.existsByProjectIdAndFreelancerId(projectId, userId);
        if (!visible) {
            throw ProjectAccess.notFound();
        }
        return ProjectResponse.from(project);
    }

    @Transactional
    public ProjectResponse update(Long projectId, Long clientId, ProjectRequest request) {
        Project project = access.loadOwned(projectId, clientId);
        if (project.getStatus() != ProjectStatus.DRAFT && project.getStatus() != ProjectStatus.OPEN) {
            throw new ApiException(HttpStatus.CONFLICT, "PROJECT_NOT_EDITABLE",
                    "A project that is " + project.getStatus() + " can no longer be edited");
        }
        BigDecimal newBudget = PriceRules.normalize(request.budgetInr());
        if (newBudget.compareTo(project.getBudgetInr()) != 0
                && proposals.existsByProjectIdAndStatusIn(projectId, ProposalTransitions.ACTIVE)) {
            throw new ApiException(HttpStatus.CONFLICT, "BUDGET_LOCKED",
                    "The budget cannot change once freelancers have proposed on this project");
        }
        if (!Objects.equals(request.deadline(), project.getDeadline())) {
            requireFutureDeadline(request.deadline());
        }
        apply(project, request);
        return ProjectResponse.from(projects.save(project));
    }

    @Transactional
    public ProjectResponse publish(Long projectId, Long clientId) {
        Project project = access.loadOwned(projectId, clientId);
        ProjectTransitions.require(project.getStatus(), ProjectStatus.OPEN);
        if (project.getDeadline() != null && !project.getDeadline().isAfter(Instant.now(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEADLINE",
                    "The deadline has already passed. Update it before publishing");
        }
        project.setStatus(ProjectStatus.OPEN);
        return ProjectResponse.from(projects.save(project));
    }

    /** OPEN to CLOSED: stop taking proposals. Open proposals are rejected. */
    @Transactional
    public ProjectResponse close(Long projectId, Long clientId) {
        return endProject(projectId, clientId, ProjectStatus.CLOSED);
    }

    /** DRAFT or OPEN to CANCELLED. Open proposals are rejected. */
    @Transactional
    public ProjectResponse cancel(Long projectId, Long clientId) {
        return endProject(projectId, clientId, ProjectStatus.CANCELLED);
    }

    private ProjectResponse endProject(Long projectId, Long clientId, ProjectStatus target) {
        Project project = access.loadOwned(projectId, clientId);
        ProjectTransitions.require(project.getStatus(), target);
        project.setStatus(target);
        projects.saveAndFlush(project);
        workflow.rejectOpenProposals(project.getId(), null);
        return ProjectResponse.from(project);
    }

    private void apply(Project project, ProjectRequest request) {
        project.setTitle(request.title().trim());
        project.setDescription(request.description().trim());
        project.setCategory(request.category().trim());
        project.setSkills(PriceRules.blankToNull(request.skills()));
        project.setBudgetInr(PriceRules.normalize(request.budgetInr()));
        project.setDeadline(request.deadline());
    }

    private void requireFutureDeadline(Instant deadline) {
        if (deadline != null && !deadline.isAfter(Instant.now(clock))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEADLINE", "The deadline must be in the future");
        }
    }
}
