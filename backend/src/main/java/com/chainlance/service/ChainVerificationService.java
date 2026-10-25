package com.chainlance.service;

import com.chainlance.chain.ChainReader;
import com.chainlance.chain.EscrowCreatedEvent;
import com.chainlance.chain.EscrowMilestoneView;
import com.chainlance.chain.FundedEvent;
import com.chainlance.chain.OnchainProjectId;
import com.chainlance.config.ChainProperties;
import com.chainlance.dto.AgreementResponse;
import com.chainlance.dto.MilestoneResponse;
import com.chainlance.dto.ReconcileResponse;
import com.chainlance.dto.TransactionResponse;
import com.chainlance.dto.VerifyTransactionRequest;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.BlockchainTransaction;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.Proposal;
import com.chainlance.entity.User;
import com.chainlance.entity.enums.AgreementStatus;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.entity.enums.MilestoneWorkStatus;
import com.chainlance.entity.enums.Role;
import com.chainlance.entity.enums.TransactionStatus;
import com.chainlance.entity.enums.TransactionType;
import com.chainlance.repository.AgreementRepository;
import com.chainlance.repository.BlockchainTransactionRepository;
import com.chainlance.repository.MilestoneRepository;
import com.chainlance.repository.UserRepository;
import com.chainlance.web.ApiException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@Service
public class ChainVerificationService {

    private final ChainReader chainReader;
    private final ChainProperties chainProperties;
    private final AgreementRepository agreements;
    private final MilestoneRepository milestones;
    private final BlockchainTransactionRepository transactions;
    private final UserRepository users;
    private final MilestoneService milestoneService;
    private final AgreementService agreementService;
    private final PriceConverter converter;
    private final Clock clock;

    public ChainVerificationService(ChainReader chainReader, ChainProperties chainProperties,
                                    AgreementRepository agreements, MilestoneRepository milestones,
                                    BlockchainTransactionRepository transactions, UserRepository users,
                                    MilestoneService milestoneService, AgreementService agreementService,
                                    PriceConverter converter, Clock clock) {
        this.chainReader = chainReader;
        this.chainProperties = chainProperties;
        this.agreements = agreements;
        this.milestones = milestones;
        this.transactions = transactions;
        this.users = users;
        this.milestoneService = milestoneService;
        this.agreementService = agreementService;
        this.converter = converter;
        this.clock = clock;
    }

    /**
     * Client only: registers the deployed escrow contract by verifying the createEscrow transaction on-chain.
     * Idempotent: returns existing agreement if already registered with the same escrow.
     */
    @Transactional
    public AgreementResponse registerEscrow(Long agreementId, Long currentUserId, String txHash) {
        Agreement agreement = agreements.findById(agreementId).orElseThrow(ChainVerificationService::notFoundAgreement);
        Proposal proposal = agreement.getProposal();

        if (!ProposalAccess.isClient(proposal, currentUserId)) {
            throw notFoundAgreement();
        }

        if (agreement.getStatus() == AgreementStatus.DRAFT) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "AGREEMENT_NOT_FINALIZED", "Agreement must be finalized");
        }

        if (agreement.getClientWallet() == null || agreement.getClientWallet().isBlank()
                || agreement.getFreelancerWallet() == null || agreement.getFreelancerWallet().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WALLETS_NOT_SET",
                    "Both client and freelancer wallets must be selected before registering an escrow");
        }

        if (agreement.getClientWallet().equalsIgnoreCase(agreement.getFreelancerWallet())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "WALLETS_NOT_DISTINCT",
                    "Client and freelancer wallets must be distinct addresses");
        }

        String normalizedTxHash = txHash.trim().toLowerCase();

        // Check if transaction was already recorded
        Optional<BlockchainTransaction> existingTx = transactions.findByTxHash(normalizedTxHash);
        if (existingTx.isPresent()) {
            BlockchainTransaction tx = existingTx.get();
            if (tx.getStatus() == TransactionStatus.CONFIRMED && agreement.getEscrowAddress() != null) {
                return agreementService.get(agreementId, currentUserId);
            }
            if (tx.getStatus() == TransactionStatus.FAILED) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TX_ALREADY_FAILED",
                        "Transaction was verified as failed: " + tx.getError());
            }
        }

        // Must have milestones generated
        List<Milestone> agreementMilestones = milestones.findByAgreementIdOrderByPositionAsc(agreementId);
        if (agreementMilestones.isEmpty()) {
            milestoneService.generateMilestones(agreementId, currentUserId);
            agreementMilestones = milestones.findByAgreementIdOrderByPositionAsc(agreementId);
        }

        String expectedProjectId = OnchainProjectId.compute(agreement.getId(), agreement.getSnapshotHash()).toLowerCase();

        // Read receipt from chain
        TransactionReceipt receipt = chainReader.getReceipt(normalizedTxHash).orElseThrow(
                () -> new ApiException(HttpStatus.BAD_REQUEST, "TX_NOT_FOUND",
                        "Transaction not found on chain yet. Please check the hash or wait for block confirmation")
        );

        User clientUser = proposal.getProject().getClient();

        // 1. Verify receipt status == 1
        if (!"0x1".equalsIgnoreCase(receipt.getStatus()) && !"1".equals(receipt.getStatus())) {
            recordFailure(normalizedTxHash, TransactionType.CREATE_ESCROW, agreement, null, clientUser, receipt,
                    "Transaction reverted on chain");
            throw new ApiException(HttpStatus.BAD_REQUEST, "TX_REVERTED", "The escrow creation transaction reverted on chain");
        }

        // 2. Verify confirmations
        long currentBlock = chainReader.getBlockNumber();
        long txBlock = receipt.getBlockNumber() != null ? receipt.getBlockNumber().longValue() : currentBlock;
        long confirmations = currentBlock >= txBlock ? (currentBlock - txBlock + 1) : 0;
        if (confirmations < chainProperties.requiredConfirmations()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_ENOUGH_CONFIRMATIONS",
                    "Transaction has " + confirmations + " confirmation(s), but " + chainProperties.requiredConfirmations() + " are required");
        }

        // 3. Verify 'to' address matches factory
        String configuredFactory = chainProperties.factoryAddress();
        if (configuredFactory != null && !configuredFactory.isBlank()) {
            if (receipt.getTo() == null || !configuredFactory.equalsIgnoreCase(receipt.getTo())) {
                recordFailure(normalizedTxHash, TransactionType.CREATE_ESCROW, agreement, null, clientUser, receipt,
                        "Transaction 'to' address (" + receipt.getTo() + ") does not match factory (" + configuredFactory + ")");
                throw new ApiException(HttpStatus.BAD_REQUEST, "FACTORY_MISMATCH",
                        "Transaction target address does not match configured factory address");
            }
        }

        // 4. Verify EscrowCreated event
        List<EscrowCreatedEvent> events = chainReader.findEscrowCreatedEvents(receipt, configuredFactory);
        if (events.isEmpty()) {
            recordFailure(normalizedTxHash, TransactionType.CREATE_ESCROW, agreement, null, clientUser, receipt,
                    "No EscrowCreated event found in receipt");
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_ESCROW_EVENT", "No EscrowCreated event was emitted by the factory");
        }
        if (events.size() > 1) {
            recordFailure(normalizedTxHash, TransactionType.CREATE_ESCROW, agreement, null, clientUser, receipt,
                    "Multiple EscrowCreated events in transaction");
            throw new ApiException(HttpStatus.BAD_REQUEST, "MULTIPLE_ESCROWS", "Transaction emitted multiple EscrowCreated events");
        }

        EscrowCreatedEvent event = events.get(0);

        // 5. Verify event parameters against agreement
        if (!expectedProjectId.equalsIgnoreCase(event.projectId())) {
            recordFailure(normalizedTxHash, TransactionType.CREATE_ESCROW, agreement, null, clientUser, receipt,
                    "Event projectId (" + event.projectId() + ") does not match agreement (" + expectedProjectId + ")");
            throw new ApiException(HttpStatus.BAD_REQUEST, "PROJECT_ID_MISMATCH",
                    "Escrow projectId does not match this agreement's snapshot");
        }

        if (!agreement.getClientWallet().equalsIgnoreCase(event.client())) {
            recordFailure(normalizedTxHash, TransactionType.CREATE_ESCROW, agreement, null, clientUser, receipt,
                    "Event client (" + event.client() + ") does not match agreement client (" + agreement.getClientWallet() + ")");
            throw new ApiException(HttpStatus.BAD_REQUEST, "CLIENT_MISMATCH",
                    "Escrow client address does not match agreement client wallet");
        }

        if (!agreement.getFreelancerWallet().equalsIgnoreCase(event.freelancer())) {
            recordFailure(normalizedTxHash, TransactionType.CREATE_ESCROW, agreement, null, clientUser, receipt,
                    "Event freelancer (" + event.freelancer() + ") does not match agreement freelancer (" + agreement.getFreelancerWallet() + ")");
            throw new ApiException(HttpStatus.BAD_REQUEST, "FREELANCER_MISMATCH",
                    "Escrow freelancer address does not match agreement freelancer wallet");
        }

        if (chainProperties.adminAddress() != null && !chainProperties.adminAddress().isBlank()) {
            if (!chainProperties.adminAddress().equalsIgnoreCase(event.admin())) {
                recordFailure(normalizedTxHash, TransactionType.CREATE_ESCROW, agreement, null, clientUser, receipt,
                        "Event admin (" + event.admin() + ") does not match configured admin (" + chainProperties.adminAddress() + ")");
                throw new ApiException(HttpStatus.BAD_REQUEST, "ADMIN_MISMATCH",
                        "Escrow admin address does not match configured platform admin");
            }
        }

        if (agreement.getTotalWei().toBigInteger().compareTo(event.totalAmount()) != 0) {
            recordFailure(normalizedTxHash, TransactionType.CREATE_ESCROW, agreement, null, clientUser, receipt,
                    "Event total (" + event.totalAmount() + ") does not match agreement totalWei (" + agreement.getTotalWei() + ")");
            throw new ApiException(HttpStatus.BAD_REQUEST, "TOTAL_AMOUNT_MISMATCH",
                    "Escrow total amount does not match agreement total wei");
        }

        // 6. Verify deployed escrow view state
        String deployedEscrow = event.escrow().toLowerCase();
        int onchainMilestones = chainReader.getMilestoneCount(deployedEscrow);
        if (onchainMilestones != agreementMilestones.size()) {
            recordFailure(normalizedTxHash, TransactionType.CREATE_ESCROW, agreement, null, clientUser, receipt,
                    "Onchain milestone count (" + onchainMilestones + ") != agreement (" + agreementMilestones.size() + ")");
            throw new ApiException(HttpStatus.BAD_REQUEST, "MILESTONE_COUNT_MISMATCH",
                    "Escrow milestone count does not match agreement milestone count");
        }

        for (int i = 0; i < agreementMilestones.size(); i++) {
            EscrowMilestoneView mView = chainReader.getMilestone(deployedEscrow, i);
            if (agreementMilestones.get(i).getAmountWei().toBigInteger().compareTo(mView.allocation()) != 0) {
                recordFailure(normalizedTxHash, TransactionType.CREATE_ESCROW, agreement, null, clientUser, receipt,
                        "Milestone " + i + " allocation mismatch");
                throw new ApiException(HttpStatus.BAD_REQUEST, "ALLOCATION_MISMATCH",
                        "Milestone " + i + " allocation on-chain does not match agreement");
            }
        }

        // If agreement already has an escrow address set, ensure it matches
        if (agreement.getEscrowAddress() != null && !agreement.getEscrowAddress().equalsIgnoreCase(deployedEscrow)) {
            throw new ApiException(HttpStatus.CONFLICT, "ESCROW_ALREADY_SET",
                    "Agreement already has a different escrow registered (" + agreement.getEscrowAddress() + ")");
        }

        // All checks passed! Update agreement
        agreement.setEscrowAddress(deployedEscrow);
        agreement.setFactoryAddress(receipt.getTo().toLowerCase());
        agreement.setChainId(chainProperties.chainId());
        agreement.setOnchainProjectId(expectedProjectId);
        Agreement savedAgreement = agreements.save(agreement);

        // Record confirmed transaction
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("escrow", deployedEscrow);
        eventData.put("client", event.client());
        eventData.put("freelancer", event.freelancer());
        eventData.put("admin", event.admin());
        eventData.put("totalAmount", event.totalAmount().toString());
        eventData.put("projectId", event.projectId());

        BlockchainTransaction tx = new BlockchainTransaction();
        tx.setTxHash(normalizedTxHash);
        tx.setType(TransactionType.CREATE_ESCROW);
        tx.setStatus(TransactionStatus.CONFIRMED);
        tx.setAgreement(savedAgreement);
        tx.setSubmittedBy(clientUser);
        tx.setFromAddress(receipt.getFrom() != null ? receipt.getFrom().toLowerCase() : null);
        tx.setToAddress(receipt.getTo() != null ? receipt.getTo().toLowerCase() : null);
        tx.setChainId(chainProperties.chainId());
        tx.setBlockNumber(txBlock);
        tx.setValueWei(BigDecimal.ZERO);
        tx.setEvent(eventData);
        tx.setVerifiedAt(Instant.now(clock));
        transactions.save(tx);

        return agreementService.get(savedAgreement.getId(), currentUserId);
    }

    /**
     * Verifies a funding transaction.
     * Updates agreements.status = FUNDED and activates milestone 0 to IN_PROGRESS.
     */
    @Transactional
    public TransactionResponse verifyFunding(String txHash, Long agreementId, Long currentUserId) {
        String normalizedTxHash = txHash.trim().toLowerCase();

        Agreement agreement = agreements.findById(agreementId).orElseThrow(ChainVerificationService::notFoundAgreement);
        Proposal proposal = agreement.getProposal();

        if (!ProposalAccess.isClient(proposal, currentUserId) && !ProposalAccess.isFreelancer(proposal, currentUserId)) {
            throw notFoundAgreement();
        }

        if (agreement.getEscrowAddress() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "ESCROW_NOT_REGISTERED", "Escrow is not registered yet");
        }

        // Check if already verified
        Optional<BlockchainTransaction> existingTx = transactions.findByTxHash(normalizedTxHash);
        if (existingTx.isPresent()) {
            BlockchainTransaction tx = existingTx.get();
            if (tx.getStatus() == TransactionStatus.CONFIRMED) {
                return TransactionResponse.from(tx);
            }
            if (tx.getStatus() == TransactionStatus.FAILED) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "TX_ALREADY_FAILED",
                        "Transaction was verified as failed: " + tx.getError());
            }
        }

        TransactionReceipt receipt = chainReader.getReceipt(normalizedTxHash).orElseThrow(
                () -> new ApiException(HttpStatus.BAD_REQUEST, "TX_NOT_FOUND", "Transaction not found on chain yet")
        );

        User caller = users.findById(currentUserId).orElseThrow();

        // 1. Status == 1
        if (!"0x1".equalsIgnoreCase(receipt.getStatus()) && !"1".equals(receipt.getStatus())) {
            recordFailure(normalizedTxHash, TransactionType.FUND, agreement, null, caller, receipt,
                    "Funding transaction reverted on chain");
            throw new ApiException(HttpStatus.BAD_REQUEST, "TX_REVERTED", "Funding transaction reverted on chain");
        }

        // 2. Confirmations
        long currentBlock = chainReader.getBlockNumber();
        long txBlock = receipt.getBlockNumber() != null ? receipt.getBlockNumber().longValue() : currentBlock;
        long confirmations = currentBlock >= txBlock ? (currentBlock - txBlock + 1) : 0;
        if (confirmations < chainProperties.requiredConfirmations()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NOT_ENOUGH_CONFIRMATIONS",
                    "Transaction has " + confirmations + " confirmation(s), but " + chainProperties.requiredConfirmations() + " are required");
        }

        // 3. 'to' address == escrowAddress
        if (receipt.getTo() == null || !receipt.getTo().equalsIgnoreCase(agreement.getEscrowAddress())) {
            recordFailure(normalizedTxHash, TransactionType.FUND, agreement, null, caller, receipt,
                    "Transaction target (" + receipt.getTo() + ") does not match escrow address (" + agreement.getEscrowAddress() + ")");
            throw new ApiException(HttpStatus.BAD_REQUEST, "ESCROW_ADDRESS_MISMATCH",
                    "Transaction was not sent to the registered escrow address");
        }

        // 4. Funded event
        List<FundedEvent> events = chainReader.findFundedEvents(receipt, agreement.getEscrowAddress());
        if (events.isEmpty()) {
            recordFailure(normalizedTxHash, TransactionType.FUND, agreement, null, caller, receipt,
                    "No Funded event found in transaction logs");
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_FUNDED_EVENT", "No Funded event emitted by escrow");
        }

        FundedEvent event = events.get(0);
        if (!agreement.getClientWallet().equalsIgnoreCase(event.client())) {
            recordFailure(normalizedTxHash, TransactionType.FUND, agreement, null, caller, receipt,
                    "Funder (" + event.client() + ") does not match client wallet (" + agreement.getClientWallet() + ")");
            throw new ApiException(HttpStatus.BAD_REQUEST, "FUNDER_MISMATCH",
                    "Funder wallet does not match agreement client wallet");
        }

        if (agreement.getTotalWei().toBigInteger().compareTo(event.amount()) != 0) {
            recordFailure(normalizedTxHash, TransactionType.FUND, agreement, null, caller, receipt,
                    "Funded amount (" + event.amount() + ") does not match totalWei (" + agreement.getTotalWei() + ")");
            throw new ApiException(HttpStatus.BAD_REQUEST, "AMOUNT_MISMATCH",
                    "Funded amount does not match total agreed wei");
        }

        // 5. Escrow view funded() == true
        if (!chainReader.isFunded(agreement.getEscrowAddress())) {
            recordFailure(normalizedTxHash, TransactionType.FUND, agreement, null, caller, receipt,
                    "Escrow view funded() is false");
            throw new ApiException(HttpStatus.BAD_REQUEST, "ESCROW_NOT_FUNDED", "Escrow is not marked as funded");
        }

        // All checks pass: update agreement to FUNDED
        agreement.setStatus(AgreementStatus.FUNDED);
        agreements.save(agreement);

        // Update milestones: start first milestone
        List<Milestone> mList = milestones.findByAgreementIdOrderByPositionAsc(agreement.getId());
        if (!mList.isEmpty()) {
            Milestone m0 = mList.get(0);
            if (m0.getWorkStatus() == MilestoneWorkStatus.PENDING) {
                m0.setWorkStatus(MilestoneWorkStatus.IN_PROGRESS);
                milestones.save(m0);
            }
        }

        Map<String, Object> eventData = Map.of(
                "client", event.client(),
                "amount", event.amount().toString()
        );

        BlockchainTransaction tx = new BlockchainTransaction();
        tx.setTxHash(normalizedTxHash);
        tx.setType(TransactionType.FUND);
        tx.setStatus(TransactionStatus.CONFIRMED);
        tx.setAgreement(agreement);
        tx.setSubmittedBy(caller);
        tx.setFromAddress(receipt.getFrom() != null ? receipt.getFrom().toLowerCase() : null);
        tx.setToAddress(receipt.getTo() != null ? receipt.getTo().toLowerCase() : null);
        tx.setChainId(chainProperties.chainId());
        tx.setBlockNumber(txBlock);
        tx.setValueWei(agreement.getTotalWei());
        tx.setEvent(eventData);
        tx.setVerifiedAt(Instant.now(clock));

        return TransactionResponse.from(transactions.save(tx));
    }

    /**
     * General verify endpoint: supports FUND and other transaction types.
     */
    @Transactional
    public TransactionResponse verifyTransaction(VerifyTransactionRequest request, Long currentUserId) {
        if (request.type() == TransactionType.CREATE_ESCROW) {
            if (request.agreementId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AGREEMENT_REQUIRED", "agreementId is required for CREATE_ESCROW");
            }
            registerEscrow(request.agreementId(), currentUserId, request.txHash());
            return TransactionResponse.from(transactions.findByTxHash(request.txHash().trim().toLowerCase()).orElseThrow());
        }
        if (request.type() == TransactionType.FUND) {
            if (request.agreementId() == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "AGREEMENT_REQUIRED", "agreementId is required for FUND");
            }
            return verifyFunding(request.txHash(), request.agreementId(), currentUserId);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_TYPE", "Transaction type " + request.type() + " is not yet supported");
    }

    /**
     * Reconciles contract state forward into MySQL.
     */
    @Transactional
    public ReconcileResponse reconcile(Long agreementId, Long currentUserId, Role currentRole) {
        Agreement agreement = agreements.findById(agreementId).orElseThrow(ChainVerificationService::notFoundAgreement);
        Proposal proposal = agreement.getProposal();

        boolean isClient = ProposalAccess.isClient(proposal, currentUserId);
        boolean isFreelancer = ProposalAccess.isFreelancer(proposal, currentUserId);
        boolean isAdmin = currentRole == Role.ADMIN;

        if (!isClient && !isFreelancer && !isAdmin) {
            throw notFoundAgreement();
        }

        if (agreement.getEscrowAddress() == null) {
            List<MilestoneResponse> mResponses = milestones.findByAgreementIdOrderByPositionAsc(agreementId).stream()
                    .map(m -> MilestoneResponse.from(m, converter.inrPerEth()))
                    .toList();
            return new ReconcileResponse(agreementId, agreement.getStatus(), mResponses, Instant.now(clock));
        }

        String escrow = agreement.getEscrowAddress();
        boolean funded = chainReader.isFunded(escrow);
        boolean completed = chainReader.isCompleted(escrow);

        if (funded && agreement.getStatus() == AgreementStatus.FINALIZED) {
            agreement.setStatus(AgreementStatus.FUNDED);
        }
        if (completed && agreement.getStatus() != AgreementStatus.COMPLETED) {
            agreement.setStatus(AgreementStatus.COMPLETED);
        }
        agreements.save(agreement);

        List<Milestone> mList = milestones.findByAgreementIdOrderByPositionAsc(agreementId);
        int onchainCount = chainReader.getMilestoneCount(escrow);

        for (int i = 0; i < Math.min(onchainCount, mList.size()); i++) {
            EscrowMilestoneView view = chainReader.getMilestone(escrow, i);
            Milestone m = mList.get(i);
            m.setFreelancerPaidWei(new BigDecimal(view.freelancerPaid()));
            m.setClientRefundedWei(new BigDecimal(view.clientRefunded()));

            switch (view.status()) {
                case 1 -> {
                    m.setChainStatus(MilestoneChainStatus.RELEASED);
                    if (m.getWorkStatus() != MilestoneWorkStatus.APPROVED) {
                        m.setWorkStatus(MilestoneWorkStatus.APPROVED);
                    }
                }
                case 2 -> {
                    m.setChainStatus(MilestoneChainStatus.SETTLED);
                    m.setWorkStatus(MilestoneWorkStatus.CLOSED);
                }
                case 3 -> m.setChainStatus(MilestoneChainStatus.DISPUTED);
                case 4 -> {
                    m.setChainStatus(MilestoneChainStatus.DISPUTE_RESOLVED);
                    m.setWorkStatus(MilestoneWorkStatus.CLOSED);
                }
                default -> {
                    m.setChainStatus(MilestoneChainStatus.OPEN);
                    if (funded && i == 0 && m.getWorkStatus() == MilestoneWorkStatus.PENDING) {
                        m.setWorkStatus(MilestoneWorkStatus.IN_PROGRESS);
                    }
                }
            }
            milestones.save(m);
        }

        List<MilestoneResponse> mResponses = mList.stream()
                .map(m -> MilestoneResponse.from(m, converter.inrPerEth()))
                .toList();

        return new ReconcileResponse(agreementId, agreement.getStatus(), mResponses, Instant.now(clock));
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> listMine(Long userId) {
        return transactions.findBySubmittedByIdOrderByCreatedAtDesc(userId).stream()
                .map(TransactionResponse::from)
                .toList();
    }

    private void recordFailure(String txHash, TransactionType type, Agreement agreement, Milestone milestone,
                               User submittedBy, TransactionReceipt receipt, String errorMessage) {
        BlockchainTransaction tx = new BlockchainTransaction();
        tx.setTxHash(txHash);
        tx.setType(type);
        tx.setStatus(TransactionStatus.FAILED);
        tx.setAgreement(agreement);
        tx.setMilestone(milestone);
        tx.setSubmittedBy(submittedBy);
        if (receipt != null) {
            tx.setFromAddress(receipt.getFrom() != null ? receipt.getFrom().toLowerCase() : null);
            tx.setToAddress(receipt.getTo() != null ? receipt.getTo().toLowerCase() : null);
            if (receipt.getBlockNumber() != null) {
                tx.setBlockNumber(receipt.getBlockNumber().longValue());
            }
        }
        tx.setChainId(chainProperties.chainId());
        tx.setError(errorMessage.length() > 1000 ? errorMessage.substring(0, 1000) : errorMessage);
        tx.setVerifiedAt(Instant.now(clock));
        transactions.save(tx);
    }

    private static ApiException notFoundAgreement() {
        return new ApiException(HttpStatus.NOT_FOUND, "AGREEMENT_NOT_FOUND", "Agreement not found");
    }
}
