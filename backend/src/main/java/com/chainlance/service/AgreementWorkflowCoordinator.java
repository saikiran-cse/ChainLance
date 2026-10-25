package com.chainlance.service;

import com.chainlance.chain.ChainReader;
import com.chainlance.config.ChainProperties;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.BlockchainTransaction;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.Project;
import com.chainlance.entity.Proposal;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.AgreementStatus;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.entity.enums.MilestoneWorkStatus;
import com.chainlance.entity.enums.ProjectStatus;
import com.chainlance.entity.enums.TransactionStatus;
import com.chainlance.entity.enums.TransactionType;
import com.chainlance.repository.AgreementRepository;
import com.chainlance.repository.BlockchainTransactionRepository;
import com.chainlance.repository.MilestoneRepository;
import com.chainlance.repository.ProjectRepository;
import com.chainlance.web.ApiException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Component
public class AgreementWorkflowCoordinator {

    private final MilestoneRepository milestones;
    private final AgreementRepository agreements;
    private final ProjectRepository projects;
    private final BlockchainTransactionRepository transactions;
    private final ChainReader chainReader;
    private final ChainProperties chainProperties;
    private final Clock clock;

    public AgreementWorkflowCoordinator(MilestoneRepository milestones, AgreementRepository agreements,
                                        ProjectRepository projects, BlockchainTransactionRepository transactions,
                                        ChainReader chainReader, ChainProperties chainProperties, Clock clock) {
        this.milestones = milestones;
        this.agreements = agreements;
        this.projects = projects;
        this.transactions = transactions;
        this.chainReader = chainReader;
        this.chainProperties = chainProperties;
        this.clock = clock;
    }

    public Participant checkAgreementParticipant(Agreement agreement, Long userId) {
        Proposal proposal = agreement.getProposal();
        boolean isClient = ProposalAccess.isClient(proposal, userId);
        boolean isFreelancer = ProposalAccess.isFreelancer(proposal, userId);
        if (!isClient && !isFreelancer) {
            throw new ApiException(HttpStatus.NOT_FOUND, "AGREEMENT_NOT_FOUND", "Agreement not found");
        }
        return new Participant(isClient, isFreelancer);
    }

    public Participant checkMilestoneParticipant(Milestone milestone, Long userId) {
        return checkAgreementParticipant(milestone.getAgreement(), userId);
    }

    public void advanceNextMilestoneOrComplete(Agreement agreement, Milestone milestone) {
        List<Milestone> all = milestones.findByAgreementIdOrderByPositionAsc(agreement.getId());
        int nextPos = milestone.getPosition() + 1;
        if (nextPos < all.size()) {
            Milestone next = all.get(nextPos);
            if (next.getWorkStatus() == MilestoneWorkStatus.PENDING) {
                next.setWorkStatus(MilestoneWorkStatus.IN_PROGRESS);
                milestones.save(next);
            }
        }

        boolean allResolved = all.stream().allMatch(m ->
                m.getChainStatus() == MilestoneChainStatus.RELEASED
                        || m.getChainStatus() == MilestoneChainStatus.SETTLED
                        || m.getChainStatus() == MilestoneChainStatus.DISPUTE_RESOLVED
        );

        if (allResolved) {
            agreement.setStatus(AgreementStatus.COMPLETED);
            agreements.save(agreement);
            Project project = agreement.getProposal().getProject();
            project.setStatus(ProjectStatus.COMPLETED);
            projects.save(project);
        }
    }

    public TransactionReceipt verifyReceipt(String txHash, String expectedTo, TransactionType type,
                                           Agreement agreement, Milestone milestone, User user) {
        String normalized = txHash.trim().toLowerCase();
        TransactionReceipt receipt = chainReader.getReceipt(normalized).orElseThrow(
                () -> new ApiException(HttpStatus.BAD_REQUEST, "TX_NOT_FOUND", "Transaction not found on chain yet")
        );

        if (!"0x1".equalsIgnoreCase(receipt.getStatus()) && !"1".equals(receipt.getStatus())) {
            recordTx(normalized, type, TransactionStatus.FAILED, agreement, milestone, user, receipt, BigDecimal.ZERO, null, "Transaction reverted on chain");
            throw new ApiException(HttpStatus.BAD_REQUEST, "TX_REVERTED", "Transaction reverted on chain");
        }

        long currentBlock = chainReader.getBlockNumber();
        long txBlock = receipt.getBlockNumber() != null ? receipt.getBlockNumber().longValue() : currentBlock;
        long confirmations = currentBlock >= txBlock ? (currentBlock - txBlock + 1) : 0;
        if (confirmations < chainProperties.requiredConfirmations()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_ENOUGH_CONFIRMATIONS",
                    "Transaction has " + confirmations + " confirmation(s), but " + chainProperties.requiredConfirmations() + " are required");
        }

        if (expectedTo != null && (receipt.getTo() == null || !receipt.getTo().equalsIgnoreCase(expectedTo))) {
            recordTx(normalized, type, TransactionStatus.FAILED, agreement, milestone, user, receipt, BigDecimal.ZERO, null,
                    "Transaction target (" + receipt.getTo() + ") does not match expected address (" + expectedTo + ")");
            throw new ApiException(HttpStatus.BAD_REQUEST, "ADDRESS_MISMATCH", "Transaction target does not match expected contract");
        }

        return receipt;
    }

    public BlockchainTransaction recordTx(String txHash, TransactionType type, TransactionStatus status,
                                          Agreement agreement, Milestone milestone, User user,
                                          TransactionReceipt receipt, BigDecimal valueWei,
                                          Map<String, Object> eventData, String errorMessage) {
        String normalized = txHash.trim().toLowerCase();
        BlockchainTransaction tx = transactions.findByTxHash(normalized).orElseGet(() -> {
            BlockchainTransaction newTx = new BlockchainTransaction();
            newTx.setTxHash(normalized);
            return newTx;
        });

        tx.setType(type);
        tx.setStatus(status);
        tx.setAgreement(agreement);
        tx.setMilestone(milestone);
        tx.setSubmittedBy(user);
        if (receipt != null) {
            tx.setFromAddress(receipt.getFrom() != null ? receipt.getFrom().toLowerCase() : null);
            tx.setToAddress(receipt.getTo() != null ? receipt.getTo().toLowerCase() : null);
            if (receipt.getBlockNumber() != null) {
                tx.setBlockNumber(receipt.getBlockNumber().longValue());
            }
        }
        tx.setChainId(chainProperties.chainId());
        tx.setValueWei(valueWei != null ? valueWei : BigDecimal.ZERO);
        tx.setEvent(eventData);
        if (errorMessage != null) {
            tx.setError(errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage);
        }
        tx.setVerifiedAt(Instant.now(clock));
        return transactions.save(tx);
    }

    public record Participant(boolean isClient, boolean isFreelancer) {}
}
