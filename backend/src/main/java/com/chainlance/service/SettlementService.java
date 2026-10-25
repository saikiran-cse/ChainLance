package com.chainlance.service;

import com.chainlance.chain.ChainReader;
import com.chainlance.chain.MilestoneSettledEvent;
import com.chainlance.dto.AcceptSettlementRequest;
import com.chainlance.dto.ProposeSettlementRequest;
import com.chainlance.dto.SettlementResponse;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.BlockchainTransaction;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.SettlementProposal;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.entity.enums.MilestoneWorkStatus;
import com.chainlance.entity.enums.SettlementStatus;
import com.chainlance.entity.enums.TransactionStatus;
import com.chainlance.entity.enums.TransactionType;
import com.chainlance.repository.MilestoneRepository;
import com.chainlance.repository.SettlementProposalRepository;
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
public class SettlementService {

    private final SettlementProposalRepository settlements;
    private final MilestoneRepository milestones;
    private final UserRepository users;
    private final ChainReader chainReader;
    private final AgreementWorkflowCoordinator coordinator;
    private final Clock clock;

    public SettlementService(SettlementProposalRepository settlements, MilestoneRepository milestones,
                             UserRepository users, ChainReader chainReader,
                             AgreementWorkflowCoordinator coordinator, Clock clock) {
        this.settlements = settlements;
        this.milestones = milestones;
        this.users = users;
        this.chainReader = chainReader;
        this.coordinator = coordinator;
        this.clock = clock;
    }

    @Transactional
    public SettlementResponse proposeSettlement(Long milestoneId, Long userId, ProposeSettlementRequest request) {
        Milestone milestone = milestones.findById(milestoneId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MILESTONE_NOT_FOUND", "Milestone not found"));
        Agreement agreement = milestone.getAgreement();
        coordinator.checkMilestoneParticipant(milestone, userId);

        if (milestone.getChainStatus() != MilestoneChainStatus.OPEN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MILESTONE_NOT_OPEN",
                    "Settlement can only be proposed on an OPEN milestone (current: " + milestone.getChainStatus() + ")");
        }

        BigDecimal fWei;
        BigDecimal cWei;
        try {
            fWei = new BigDecimal(request.freelancerWei().trim());
            cWei = new BigDecimal(request.clientWei().trim());
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_WEI_AMOUNT", "Wei amounts must be valid integer strings");
        }

        if (fWei.signum() < 0 || cWei.signum() < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NEGATIVE_WEI_AMOUNT", "Settlement amounts cannot be negative");
        }

        if (fWei.add(cWei).compareTo(milestone.getAmountWei()) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNTS_MUST_SUM_TO_ALLOCATION",
                    "Freelancer wei (" + fWei + ") + client wei (" + cWei + ") must equal milestone allocation (" + milestone.getAmountWei() + ")");
        }

        // Supersede any existing live proposal on this milestone (enforced by DB unique index uq_settlement_one_live)
        settlements.findByMilestoneIdAndStatusIn(milestoneId,
                List.of(SettlementStatus.AWAITING_PROPOSE_TX, SettlementStatus.ACTIVE)).ifPresent(p -> {
            p.setStatus(SettlementStatus.SUPERSEDED);
            settlements.save(p);
        });

        User proposer = users.findById(userId).orElseThrow();
        String txHash = request.txHash() != null && !request.txHash().isBlank() ? request.txHash().trim() : null;

        SettlementProposal prop = new SettlementProposal();
        prop.setMilestone(milestone);
        prop.setProposer(proposer);
        prop.setFreelancerWei(fWei);
        prop.setClientWei(cWei);
        prop.setReason(request.reason());

        if (txHash != null && agreement.getEscrowAddress() != null) {
            TransactionReceipt receipt = coordinator.verifyReceipt(txHash, agreement.getEscrowAddress(),
                    TransactionType.SETTLE_PROPOSE, agreement, milestone, proposer);
            BlockchainTransaction tx = coordinator.recordTx(txHash, TransactionType.SETTLE_PROPOSE, TransactionStatus.CONFIRMED,
                    agreement, milestone, proposer, receipt, milestone.getAmountWei(),
                    Map.of("freelancerWei", fWei.toPlainString(), "clientWei", cWei.toPlainString()), null);
            prop.setProposeTx(tx);
            prop.setStatus(SettlementStatus.ACTIVE);
        } else {
            prop.setStatus(agreement.getEscrowAddress() != null ? SettlementStatus.AWAITING_PROPOSE_TX : SettlementStatus.ACTIVE);
        }

        return SettlementResponse.from(settlements.save(prop));
    }

    @Transactional
    public SettlementResponse acceptSettlement(Long settlementId, Long userId, AcceptSettlementRequest request) {
        SettlementProposal prop = settlements.findById(settlementId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SETTLEMENT_NOT_FOUND", "Settlement proposal not found"));
        Milestone milestone = prop.getMilestone();
        Agreement agreement = milestone.getAgreement();
        coordinator.checkMilestoneParticipant(milestone, userId);

        if (prop.getProposer().getId().equals(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CANNOT_ACCEPT_OWN_PROPOSAL", "Cannot accept your own settlement proposal");
        }

        if (prop.getStatus() != SettlementStatus.ACTIVE && prop.getStatus() != SettlementStatus.AWAITING_PROPOSE_TX) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_ACTIVE", "Only active settlement proposals can be accepted");
        }

        if (milestone.getChainStatus() != MilestoneChainStatus.OPEN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MILESTONE_NOT_OPEN", "Milestone is not open on-chain");
        }

        User acceptor = users.findById(userId).orElseThrow();
        String txHash = request.txHash() != null ? request.txHash().trim() : null;

        if (agreement.getEscrowAddress() != null) {
            if (txHash == null || txHash.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TX_HASH_REQUIRED", "On-chain escrow requires transaction hash to accept settlement");
            }

            TransactionReceipt receipt = coordinator.verifyReceipt(txHash, agreement.getEscrowAddress(),
                    TransactionType.SETTLE, agreement, milestone, acceptor);

            List<MilestoneSettledEvent> events = chainReader.findMilestoneSettledEvents(receipt, agreement.getEscrowAddress());
            MilestoneSettledEvent matching = events.stream()
                    .filter(e -> e.index() == milestone.getPosition())
                    .findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "NO_SETTLED_EVENT",
                            "No MilestoneSettled event found for milestone position " + milestone.getPosition()));

            if (matching.freelancerAmount().compareTo(prop.getFreelancerWei().toBigInteger()) != 0
                    || matching.clientAmount().compareTo(prop.getClientWei().toBigInteger()) != 0) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH",
                        "Settled amounts on-chain do not match proposal amounts");
            }

            BlockchainTransaction tx = coordinator.recordTx(txHash, TransactionType.SETTLE, TransactionStatus.CONFIRMED,
                    agreement, milestone, acceptor, receipt, milestone.getAmountWei(),
                    Map.of("freelancerWei", prop.getFreelancerWei().toPlainString(), "clientWei", prop.getClientWei().toPlainString()), null);

            prop.setExecutedTx(tx);
        }

        prop.setStatus(SettlementStatus.EXECUTED);
        prop.setDecidedAt(Instant.now(clock));
        settlements.save(prop);

        milestone.setChainStatus(MilestoneChainStatus.SETTLED);
        milestone.setWorkStatus(MilestoneWorkStatus.CLOSED);
        milestone.setFreelancerPaidWei(prop.getFreelancerWei());
        milestone.setClientRefundedWei(prop.getClientWei());
        milestones.save(milestone);

        coordinator.advanceNextMilestoneOrComplete(agreement, milestone);

        return SettlementResponse.from(prop);
    }

    @Transactional
    public SettlementResponse withdrawSettlement(Long settlementId, Long userId) {
        SettlementProposal prop = settlements.findById(settlementId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "SETTLEMENT_NOT_FOUND", "Settlement proposal not found"));
        coordinator.checkMilestoneParticipant(prop.getMilestone(), userId);

        if (!prop.getProposer().getId().equals(userId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the proposer can withdraw the settlement proposal");
        }

        if (prop.getStatus() != SettlementStatus.ACTIVE && prop.getStatus() != SettlementStatus.AWAITING_PROPOSE_TX) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_ACTIVE", "Only active proposals can be withdrawn");
        }

        prop.setStatus(SettlementStatus.WITHDRAWN);
        prop.setDecidedAt(Instant.now(clock));
        return SettlementResponse.from(settlements.save(prop));
    }

    @Transactional(readOnly = true)
    public List<SettlementResponse> getSettlements(Long milestoneId, Long userId) {
        Milestone milestone = milestones.findById(milestoneId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "MILESTONE_NOT_FOUND", "Milestone not found"));
        coordinator.checkMilestoneParticipant(milestone, userId);
        return settlements.findByMilestoneIdOrderByCreatedAtDesc(milestoneId).stream()
                .map(SettlementResponse::from)
                .toList();
    }
}
