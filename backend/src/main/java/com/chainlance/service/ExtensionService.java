package com.chainlance.service;

import com.chainlance.dto.CreateExtensionRequest;
import com.chainlance.dto.DecisionRequest;
import com.chainlance.dto.ExtensionResponse;
import com.chainlance.entity.DeadlineExtensionRequest;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.ExtensionStatus;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.entity.enums.MilestoneWorkStatus;
import com.chainlance.repository.DeadlineExtensionRequestRepository;
import com.chainlance.repository.MilestoneRepository;
import com.chainlance.repository.UserRepository;
import com.chainlance.web.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExtensionService {

    private final DeadlineExtensionRequestRepository extensions;
    private final MilestoneRepository milestones;
    private final UserRepository users;
    private final AgreementWorkflowCoordinator coordinator;
    private final Clock clock;

    public ExtensionService(DeadlineExtensionRequestRepository extensions, MilestoneRepository milestones,
                            UserRepository users, AgreementWorkflowCoordinator coordinator, Clock clock) {
        this.extensions = extensions;
        this.milestones = milestones;
        this.users = users;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    @Transactional
    public ExtensionResponse requestExtension(Long milestoneId, Long userId, CreateExtensionRequest request) {
        Milestone milestone = milestones.findById(milestoneId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MILESTONE_NOT_FOUND", "Milestone not found"));
        var part = coordinator.checkMilestoneParticipant(milestone, userId);
        if (!part.isFreelancer()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the freelancer can request a deadline extension");
        }

        if (milestone.getWorkStatus() != MilestoneWorkStatus.IN_PROGRESS && milestone.getWorkStatus() != MilestoneWorkStatus.REVISION_REQUESTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WORK_STATUS",
                    "Extensions can only be requested while milestone is IN_PROGRESS or REVISION_REQUESTED");
        }

        if (milestone.getChainStatus() != MilestoneChainStatus.OPEN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MILESTONE_NOT_OPEN", "Milestone is not open on-chain");
        }

        if (extensions.findByMilestoneIdAndStatus(milestoneId, ExtensionStatus.PENDING).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EXTENSION_ALREADY_PENDING",
                    "A pending extension request already exists for this milestone");
        }

        Instant currentDeadline = milestone.getEffectiveDeadline();
        if (currentDeadline != null && !request.requestedDeadline().isAfter(currentDeadline)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DEADLINE",
                    "Requested deadline must be strictly after the current deadline (" + currentDeadline + ")");
        }

        User freelancer = users.findById(userId).orElseThrow();

        DeadlineExtensionRequest ext = new DeadlineExtensionRequest();
        ext.setMilestone(milestone);
        ext.setRequestedBy(freelancer);
        ext.setCurrentDeadline(currentDeadline != null ? currentDeadline : Instant.now(clock));
        ext.setRequestedDeadline(request.requestedDeadline());
        ext.setReason(request.reason());
        ext.setStatus(ExtensionStatus.PENDING);

        return ExtensionResponse.from(extensions.save(ext));
    }

    @Transactional
    public ExtensionResponse approveExtension(Long extensionId, Long userId, DecisionRequest request) {
        DeadlineExtensionRequest ext = extensions.findById(extensionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EXTENSION_NOT_FOUND", "Extension request not found"));
        Milestone milestone = ext.getMilestone();
        var part = coordinator.checkMilestoneParticipant(milestone, userId);
        if (!part.isClient()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the client can approve deadline extensions");
        }

        if (ext.getStatus() != ExtensionStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_PENDING", "Only pending extension requests can be approved");
        }

        User clientUser = users.findById(userId).orElseThrow();
        ext.setStatus(ExtensionStatus.APPROVED);
        ext.setDecidedBy(clientUser);
        ext.setDecidedAt(Instant.now(clock));
        if (request != null && request.note() != null) {
            ext.setDecisionNote(request.note());
        }
        extensions.save(ext);

        milestone.setEffectiveDeadline(ext.getRequestedDeadline());
        milestones.save(milestone);

        return ExtensionResponse.from(ext);
    }

    @Transactional
    public ExtensionResponse rejectExtension(Long extensionId, Long userId, DecisionRequest request) {
        DeadlineExtensionRequest ext = extensions.findById(extensionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "EXTENSION_NOT_FOUND", "Extension request not found"));
        Milestone milestone = ext.getMilestone();
        var part = coordinator.checkMilestoneParticipant(milestone, userId);
        if (!part.isClient()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the client can reject deadline extensions");
        }

        if (ext.getStatus() != ExtensionStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_PENDING", "Only pending extension requests can be rejected");
        }

        User clientUser = users.findById(userId).orElseThrow();
        ext.setStatus(ExtensionStatus.REJECTED);
        ext.setDecidedBy(clientUser);
        ext.setDecidedAt(Instant.now(clock));
        if (request != null && request.note() != null) {
            ext.setDecisionNote(request.note());
        }
        return ExtensionResponse.from(extensions.save(ext));
    }

    @Transactional(readOnly = true)
    public List<ExtensionResponse> getExtensions(Long milestoneId, Long userId) {
        Milestone milestone = milestones.findById(milestoneId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MILESTONE_NOT_FOUND", "Milestone not found"));
        coordinator.checkMilestoneParticipant(milestone, userId);
        return extensions.findByMilestoneIdOrderByRequestedAtDesc(milestoneId).stream()
                .map(ExtensionResponse::from)
                .toList();
    }
}
