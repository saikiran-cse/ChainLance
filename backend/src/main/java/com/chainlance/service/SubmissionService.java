package com.chainlance.service;

import com.chainlance.chain.ChainReader;
import com.chainlance.chain.MilestoneReleasedEvent;
import com.chainlance.dto.ApproveSubmissionRequest;
import com.chainlance.dto.RevisionRequest;
import com.chainlance.dto.SubmissionResponse;
import com.chainlance.dto.SubmitWorkRequest;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.Submission;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.AgreementStatus;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.entity.enums.MilestoneWorkStatus;
import com.chainlance.entity.enums.SubmissionReviewStatus;
import com.chainlance.entity.enums.TransactionStatus;
import com.chainlance.entity.enums.TransactionType;
import com.chainlance.repository.MilestoneRepository;
import com.chainlance.repository.SubmissionRepository;
import com.chainlance.repository.UserRepository;
import com.chainlance.web.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Service
public class SubmissionService {

    private final SubmissionRepository submissions;
    private final MilestoneRepository milestones;
    private final UserRepository users;
    private final ChainReader chainReader;
    private final AgreementWorkflowCoordinator coordinator;
    private final Clock clock;

    public SubmissionService(SubmissionRepository submissions, MilestoneRepository milestones,
                             UserRepository users, ChainReader chainReader,
                             AgreementWorkflowCoordinator coordinator, Clock clock) {
        this.submissions = submissions;
        this.milestones = milestones;
        this.users = users;
        this.chainReader = chainReader;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    @Transactional
    public SubmissionResponse submitWork(Long milestoneId, Long userId, SubmitWorkRequest request) {
        Milestone milestone = milestones.findById(milestoneId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MILESTONE_NOT_FOUND", "Milestone not found"));
        Agreement agreement = milestone.getAgreement();
        var part = coordinator.checkMilestoneParticipant(milestone, userId);
        if (!part.isFreelancer()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the freelancer can submit work");
        }

        if (agreement.getStatus() != AgreementStatus.FUNDED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AGREEMENT_NOT_FUNDED", "Agreement must be funded to submit work");
        }

        if (milestone.getWorkStatus() != MilestoneWorkStatus.IN_PROGRESS && milestone.getWorkStatus() != MilestoneWorkStatus.REVISION_REQUESTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WORK_STATUS",
                    "Submissions are only allowed when milestone is IN_PROGRESS or REVISION_REQUESTED (current: " + milestone.getWorkStatus() + ")");
        }

        if (milestone.getChainStatus() != MilestoneChainStatus.OPEN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MILESTONE_NOT_OPEN", "Milestone is not open on-chain");
        }

        // Supersede any earlier pending submissions
        List<Submission> existing = submissions.findByMilestoneIdOrderBySubmittedAtDesc(milestoneId);
        for (Submission s : existing) {
            if (s.getReviewStatus() == SubmissionReviewStatus.PENDING) {
                s.setReviewStatus(SubmissionReviewStatus.SUPERSEDED);
                submissions.save(s);
            }
        }

        Instant now = Instant.now(clock);
        boolean late = milestone.getEffectiveDeadline() != null && now.isAfter(milestone.getEffectiveDeadline());

        Submission sub = new Submission();
        sub.setMilestone(milestone);
        sub.setDescription(request.description());
        sub.setLinks(request.links());
        sub.setLate(late);
        sub.setReviewStatus(SubmissionReviewStatus.PENDING);
        Submission saved = submissions.save(sub);

        milestone.setWorkStatus(MilestoneWorkStatus.SUBMITTED);
        milestones.save(milestone);

        return SubmissionResponse.from(saved);
    }

    @Transactional
    public SubmissionResponse approveSubmission(Long submissionId, Long userId, ApproveSubmissionRequest request) {
        Submission sub = submissions.findById(submissionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "Submission not found"));
        Milestone milestone = sub.getMilestone();
        Agreement agreement = milestone.getAgreement();
        var part = coordinator.checkMilestoneParticipant(milestone, userId);
        if (!part.isClient()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the client can approve submissions");
        }

        if (sub.getReviewStatus() != SubmissionReviewStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_PENDING", "Only pending submissions can be approved");
        }

        if (milestone.getWorkStatus() != MilestoneWorkStatus.SUBMITTED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WORK_STATUS", "Milestone must be in SUBMITTED status to approve");
        }

        User clientUser = users.findById(userId).orElseThrow();
        String txHash = request != null && request.txHash() != null ? request.txHash().trim() : null;

        if (agreement.getEscrowAddress() != null) {
            if (txHash == null || txHash.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TX_HASH_REQUIRED", "On-chain escrow requires transaction hash to approve milestone");
            }

            TransactionReceipt receipt = coordinator.verifyReceipt(txHash, agreement.getEscrowAddress(),
                    TransactionType.APPROVE, agreement, milestone, clientUser);

            List<MilestoneReleasedEvent> events = chainReader.findMilestoneReleasedEvents(receipt, agreement.getEscrowAddress());
            MilestoneReleasedEvent matchingEvent = events.stream()
                    .filter(e -> e.index() == milestone.getPosition())
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_RELEASE_EVENT",
                            "No MilestoneReleased event found for milestone position " + milestone.getPosition()));

            if (matchingEvent.amount().compareTo(milestone.getAmountWei().toBigInteger()) != 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH",
                        "Released amount on-chain does not match milestone allocation");
            }

            coordinator.recordTx(txHash, TransactionType.APPROVE, TransactionStatus.CONFIRMED,
                    agreement, milestone, clientUser, receipt, milestone.getAmountWei(),
                    Map.of("position", milestone.getPosition(), "amount", matchingEvent.amount().toString()), null);

            milestone.setFreelancerPaidWei(milestone.getAmountWei());
            milestone.setChainStatus(MilestoneChainStatus.RELEASED);
        } else {
            milestone.setFreelancerPaidWei(milestone.getAmountWei());
            milestone.setChainStatus(MilestoneChainStatus.RELEASED);
        }

        sub.setReviewStatus(SubmissionReviewStatus.APPROVED);
        sub.setReviewedAt(Instant.now(clock));
        submissions.save(sub);

        milestone.setWorkStatus(MilestoneWorkStatus.APPROVED);
        milestones.save(milestone);

        coordinator.advanceNextMilestoneOrComplete(agreement, milestone);

        return SubmissionResponse.from(sub);
    }

    @Transactional
    public SubmissionResponse requestRevision(Long submissionId, Long userId, RevisionRequest request) {
        Submission sub = submissions.findById(submissionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SUBMISSION_NOT_FOUND", "Submission not found"));
        Milestone milestone = sub.getMilestone();
        var part = coordinator.checkMilestoneParticipant(milestone, userId);
        if (!part.isClient()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the client can request revisions");
        }

        if (sub.getReviewStatus() != SubmissionReviewStatus.PENDING) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_PENDING", "Only pending submissions can receive revision requests");
        }

        sub.setReviewStatus(SubmissionReviewStatus.REVISION_REQUESTED);
        sub.setRevisionReason(request.reason());
        sub.setReviewedAt(Instant.now(clock));
        submissions.save(sub);

        milestone.setWorkStatus(MilestoneWorkStatus.REVISION_REQUESTED);
        milestones.save(milestone);

        return SubmissionResponse.from(sub);
    }

    @Transactional(readOnly = true)
    public List<SubmissionResponse> getSubmissions(Long milestoneId, Long userId) {
        Milestone milestone = milestones.findById(milestoneId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MILESTONE_NOT_FOUND", "Milestone not found"));
        coordinator.checkMilestoneParticipant(milestone, userId);
        return submissions.findByMilestoneIdOrderBySubmittedAtDesc(milestoneId).stream()
                .map(SubmissionResponse::from)
                .toList();
    }
}
