package com.chainlance.service;

import com.chainlance.chain.ChainReader;
import com.chainlance.chain.DisputeOpenedEvent;
import com.chainlance.chain.DisputeResolvedEvent;
import com.chainlance.dto.AdminDecisionRequest;
import com.chainlance.dto.DisputeDetailResponse;
import com.chainlance.dto.DisputeReplyRequest;
import com.chainlance.dto.DisputeReplyResponse;
import com.chainlance.dto.DisputeSummaryResponse;
import com.chainlance.dto.OpenDisputeRequest;
import com.chainlance.dto.ResolveDisputeRequest;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.BlockchainTransaction;
import com.chainlance.entity.Dispute;
import com.chainlance.entity.DisputeResponse;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.DisputeDecision;
import com.chainlance.entity.enums.DisputeStatus;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.entity.enums.MilestoneWorkStatus;
import com.chainlance.entity.enums.TransactionStatus;
import com.chainlance.entity.enums.TransactionType;
import com.chainlance.repository.DisputeRepository;
import com.chainlance.repository.DisputeResponseRepository;
import com.chainlance.repository.MilestoneRepository;
import com.chainlance.repository.UserRepository;
import com.chainlance.web.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Service
public class DisputeService {

    private final DisputeRepository disputes;
    private final DisputeResponseRepository disputeResponses;
    private final MilestoneRepository milestones;
    private final UserRepository users;
    private final ChainReader chainReader;
    private final AgreementWorkflowCoordinator coordinator;
    private final Clock clock;

    public DisputeService(DisputeRepository disputes, DisputeResponseRepository disputeResponses,
                          MilestoneRepository milestones, UserRepository users,
                          ChainReader chainReader, AgreementWorkflowCoordinator coordinator,
                          Clock clock) {
        this.disputes = disputes;
        this.disputeResponses = disputeResponses;
        this.milestones = milestones;
        this.users = users;
        this.chainReader = chainReader;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    @Transactional
    public DisputeDetailResponse openDispute(Long milestoneId, Long userId, OpenDisputeRequest request) {
        Milestone milestone = milestones.findById(milestoneId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MILESTONE_NOT_FOUND", "Milestone not found"));
        Agreement agreement = milestone.getAgreement();
        coordinator.checkMilestoneParticipant(milestone, userId);

        if (milestone.getChainStatus() != MilestoneChainStatus.OPEN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MILESTONE_NOT_OPEN",
                    "Disputes can only be opened on OPEN milestones (current: " + milestone.getChainStatus() + ")");
        }

        // Check if active dispute already exists
        if (disputes.findByMilestoneIdAndStatusIn(milestoneId,
                List.of(DisputeStatus.AWAITING_OPEN_TX, DisputeStatus.OPEN, DisputeStatus.DECIDED)).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "DISPUTE_ALREADY_ACTIVE",
                    "An active dispute already exists for this milestone");
        }

        User openedBy = users.findById(userId).orElseThrow();
        String txHash = request.txHash() != null && !request.txHash().isBlank() ? request.txHash().trim() : null;

        Dispute dispute = new Dispute();
        dispute.setMilestone(milestone);
        dispute.setOpenedBy(openedBy);
        dispute.setReason(request.reason());

        if (txHash != null && agreement.getEscrowAddress() != null) {
            TransactionReceipt receipt = coordinator.verifyReceipt(txHash, agreement.getEscrowAddress(),
                    TransactionType.DISPUTE, agreement, milestone, openedBy);

            List<DisputeOpenedEvent> events = chainReader.findDisputeOpenedEvents(receipt, agreement.getEscrowAddress());
            DisputeOpenedEvent matching = events.stream()
                    .filter(e -> e.index() == milestone.getPosition())
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_DISPUTE_EVENT",
                            "No DisputeOpened event found for milestone position " + milestone.getPosition()));

            BlockchainTransaction tx = coordinator.recordTx(txHash, TransactionType.DISPUTE, TransactionStatus.CONFIRMED,
                    agreement, milestone, openedBy, receipt, BigDecimal.ZERO,
                    Map.of("position", milestone.getPosition(), "reasonHash", matching.reasonHash()), null);

            dispute.setOpenTx(tx);
            dispute.setReasonHash(matching.reasonHash());
            dispute.setStatus(DisputeStatus.OPEN);

            milestone.setChainStatus(MilestoneChainStatus.DISPUTED);
            milestones.save(milestone);
        } else {
            if (agreement.getEscrowAddress() != null) {
                dispute.setStatus(DisputeStatus.AWAITING_OPEN_TX);
            } else {
                dispute.setStatus(DisputeStatus.OPEN);
                milestone.setChainStatus(MilestoneChainStatus.DISPUTED);
                milestones.save(milestone);
            }
        }

        Dispute saved = disputes.save(dispute);
        return DisputeDetailResponse.from(saved, List.of());
    }

    @Transactional
    public DisputeReplyResponse replyToDispute(Long disputeId, Long userId, boolean isAdmin, DisputeReplyRequest request) {
        Dispute dispute = disputes.findById(disputeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DISPUTE_NOT_FOUND", "Dispute not found"));
        if (!isAdmin) {
            coordinator.checkMilestoneParticipant(dispute.getMilestone(), userId);
        }

        if (dispute.getStatus() != DisputeStatus.OPEN && dispute.getStatus() != DisputeStatus.DECIDED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DISPUTE_NOT_ACTIVE",
                    "Can only reply to OPEN or DECIDED disputes (current: " + dispute.getStatus() + ")");
        }

        User user = users.findById(userId).orElseThrow();
        DisputeResponse resp = new DisputeResponse();
        resp.setDispute(dispute);
        resp.setUser(user);
        resp.setResponse(request.response());
        resp.setEvidenceLinks(request.evidenceLinks());

        return DisputeReplyResponse.from(disputeResponses.save(resp));
    }

    @Transactional(readOnly = true)
    public DisputeDetailResponse getDispute(Long disputeId, Long userId, boolean isAdmin) {
        Dispute dispute = disputes.findById(disputeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DISPUTE_NOT_FOUND", "Dispute not found"));
        if (!isAdmin) {
            coordinator.checkMilestoneParticipant(dispute.getMilestone(), userId);
        }
        List<DisputeReplyResponse> replies = disputeResponses.findByDisputeIdOrderByCreatedAtAsc(disputeId).stream()
                .map(DisputeReplyResponse::from)
                .toList();
        return DisputeDetailResponse.from(dispute, replies);
    }

    @Transactional(readOnly = true)
    public List<DisputeDetailResponse> getDisputesForMilestone(Long milestoneId, Long userId) {
        Milestone milestone = milestones.findById(milestoneId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MILESTONE_NOT_FOUND", "Milestone not found"));
        coordinator.checkMilestoneParticipant(milestone, userId);
        return disputes.findByMilestoneIdOrderByCreatedAtDesc(milestoneId).stream()
                .map(d -> {
                    List<DisputeReplyResponse> replies = disputeResponses.findByDisputeIdOrderByCreatedAtAsc(d.getId()).stream()
                            .map(DisputeReplyResponse::from)
                            .toList();
                    return DisputeDetailResponse.from(d, replies);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DisputeSummaryResponse> adminListDisputes(Collection<DisputeStatus> statuses, Pageable pageable) {
        Collection<DisputeStatus> queryStatuses = (statuses == null || statuses.isEmpty())
                ? List.of(DisputeStatus.AWAITING_OPEN_TX, DisputeStatus.OPEN, DisputeStatus.DECIDED)
                : statuses;
        return disputes.findByStatusIn(queryStatuses, pageable).map(DisputeSummaryResponse::from);
    }

    @Transactional
    public DisputeDetailResponse adminRecordDecision(Long disputeId, Long adminUserId, AdminDecisionRequest request) {
        Dispute dispute = disputes.findById(disputeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DISPUTE_NOT_FOUND", "Dispute not found"));

        if (dispute.getStatus() != DisputeStatus.OPEN && dispute.getStatus() != DisputeStatus.DECIDED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Dispute must be OPEN to record decision");
        }

        Milestone milestone = dispute.getMilestone();
        BigDecimal totalAllocation = milestone.getAmountWei();
        BigDecimal fWei;
        BigDecimal cWei;

        if (request.decision() == DisputeDecision.FREELANCER_FULL) {
            fWei = totalAllocation;
            cWei = BigDecimal.ZERO;
        } else if (request.decision() == DisputeDecision.CLIENT_FULL) {
            fWei = BigDecimal.ZERO;
            cWei = totalAllocation;
        } else if (request.decision() == DisputeDecision.SPLIT) {
            if (request.freelancerWei() == null || request.clientWei() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNTS_REQUIRED", "Split decision requires freelancerWei and clientWei");
            }
            try {
                fWei = new BigDecimal(request.freelancerWei().trim());
                cWei = new BigDecimal(request.clientWei().trim());
            } catch (Exception ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEI_AMOUNT", "Wei amounts must be valid integer strings");
            }
            if (fWei.signum() < 0 || cWei.signum() < 0 || fWei.add(cWei).compareTo(totalAllocation) != 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNTS_MUST_SUM_TO_ALLOCATION",
                        "freelancerWei + clientWei must equal milestone allocation (" + totalAllocation + ")");
            }
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_DECISION", "Unsupported decision: " + request.decision());
        }

        User admin = users.findById(adminUserId).orElseThrow();
        dispute.setAdminDecision(request.decision());
        dispute.setAdminFreelancerWei(fWei);
        dispute.setAdminClientWei(cWei);
        dispute.setAdminExplanation(request.explanation());
        dispute.setDecidedBy(admin);
        dispute.setDecidedAt(Instant.now(clock));
        dispute.setStatus(DisputeStatus.DECIDED);

        Dispute saved = disputes.save(dispute);
        List<DisputeReplyResponse> replies = disputeResponses.findByDisputeIdOrderByCreatedAtAsc(disputeId).stream()
                .map(DisputeReplyResponse::from)
                .toList();
        return DisputeDetailResponse.from(saved, replies);
    }

    @Transactional
    public DisputeDetailResponse adminSubmitResolutionTx(Long disputeId, Long adminUserId, ResolveDisputeRequest request) {
        Dispute dispute = disputes.findById(disputeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DISPUTE_NOT_FOUND", "Dispute not found"));

        if (dispute.getStatus() != DisputeStatus.DECIDED) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DISPUTE_NOT_DECIDED", "Dispute must be DECIDED before submitting resolution tx");
        }

        Milestone milestone = dispute.getMilestone();
        Agreement agreement = milestone.getAgreement();
        User admin = users.findById(adminUserId).orElseThrow();
        String txHash = request.txHash().trim();

        if (agreement.getEscrowAddress() != null) {
            TransactionReceipt receipt = coordinator.verifyReceipt(txHash, agreement.getEscrowAddress(),
                    TransactionType.RESOLVE, agreement, milestone, admin);

            List<DisputeResolvedEvent> events = chainReader.findDisputeResolvedEvents(receipt, agreement.getEscrowAddress());
            DisputeResolvedEvent matching = events.stream()
                    .filter(e -> e.index() == milestone.getPosition())
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_RESOLVED_EVENT",
                            "No DisputeResolved event found for milestone position " + milestone.getPosition()));

            if (matching.freelancerAmount().compareTo(dispute.getAdminFreelancerWei().toBigInteger()) != 0
                    || matching.clientAmount().compareTo(dispute.getAdminClientWei().toBigInteger()) != 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH",
                        "Resolved amounts on-chain do not match admin decision");
            }

            BlockchainTransaction tx = coordinator.recordTx(txHash, TransactionType.RESOLVE, TransactionStatus.CONFIRMED,
                    agreement, milestone, admin, receipt, milestone.getAmountWei(),
                    Map.of("freelancerWei", dispute.getAdminFreelancerWei().toPlainString(),
                            "clientWei", dispute.getAdminClientWei().toPlainString(),
                            "resolutionHash", matching.resolutionHash()), null);

            dispute.setResolvedTx(tx);
            dispute.setResolutionHash(matching.resolutionHash());
        }

        dispute.setStatus(DisputeStatus.RESOLVED);
        disputes.save(dispute);

        milestone.setChainStatus(MilestoneChainStatus.DISPUTE_RESOLVED);
        milestone.setWorkStatus(MilestoneWorkStatus.CLOSED);
        milestone.setFreelancerPaidWei(dispute.getAdminFreelancerWei());
        milestone.setClientRefundedWei(dispute.getAdminClientWei());
        milestones.save(milestone);

        coordinator.advanceNextMilestoneOrComplete(agreement, milestone);

        List<DisputeReplyResponse> replies = disputeResponses.findByDisputeIdOrderByCreatedAtAsc(disputeId).stream()
                .map(DisputeReplyResponse::from)
                .toList();
        return DisputeDetailResponse.from(dispute, replies);
    }
}
