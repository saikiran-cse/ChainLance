package com.chainlance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.chainlance.chain.EscrowMilestoneView;
import com.chainlance.chain.FakeChainReader;
import com.chainlance.chain.OnchainProjectId;
import com.chainlance.config.ChainProperties;
import com.chainlance.config.PricingProperties;
import com.chainlance.dto.AgreementResponse;
import com.chainlance.dto.ReconcileResponse;
import com.chainlance.dto.TransactionResponse;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.BlockchainTransaction;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.Project;
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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

class ChainVerificationServiceTest {

    private FakeChainReader chainReader;
    private ChainProperties chainProperties;
    private AgreementRepository agreementRepository;
    private MilestoneRepository milestoneRepository;
    private BlockchainTransactionRepository transactionRepository;
    private UserRepository userRepository;
    private MilestoneService milestoneService;
    private AgreementService agreementService;
    private PriceConverter priceConverter;
    private Clock fixedClock;
    private ChainVerificationService service;

    private static final String FACTORY = "0x1111111111111111111111111111111111111111";
    private static final String ADMIN = "0x2222222222222222222222222222222222222222";
    private static final String CLIENT_ADDR = "0x3333333333333333333333333333333333333333";
    private static final String FREELANCER_ADDR = "0x4444444444444444444444444444444444444444";
    private static final String ESCROW_ADDR = "0x5555555555555555555555555555555555555555";
    private static final String SNAPSHOT_HASH = "0x411bc73d9ea3b5244542429d99a38608950885df0f5fec423c44bbe7e84cc6c4";

    @BeforeEach
    void setUp() {
        chainReader = new FakeChainReader();
        chainProperties = new ChainProperties(31337L, "http://localhost:8545", FACTORY, ADMIN, 1);
        agreementRepository = mock(AgreementRepository.class);
        milestoneRepository = mock(MilestoneRepository.class);
        transactionRepository = mock(BlockchainTransactionRepository.class);
        userRepository = mock(UserRepository.class);
        milestoneService = mock(MilestoneService.class);
        agreementService = mock(AgreementService.class);
        priceConverter = new PriceConverter(new PricingProperties(new BigDecimal("250000")));
        fixedClock = Clock.fixed(Instant.parse("2026-10-25T12:00:00Z"), ZoneOffset.UTC);

        service = new ChainVerificationService(
                chainReader,
                chainProperties,
                agreementRepository,
                milestoneRepository,
                transactionRepository,
                userRepository,
                milestoneService,
                agreementService,
                priceConverter,
                fixedClock
        );
    }

    private Agreement createAgreement(Long id) {
        User client = new User();
        ReflectionTestUtils.setField(client, "id", 1L);
        client.setRole(Role.CLIENT);

        User freelancer = new User();
        ReflectionTestUtils.setField(freelancer, "id", 2L);
        freelancer.setRole(Role.FREELANCER);

        Project project = new Project();
        ReflectionTestUtils.setField(project, "id", 100L);
        project.setClient(client);

        Proposal proposal = new Proposal();
        ReflectionTestUtils.setField(proposal, "id", 200L);
        proposal.setProject(project);
        proposal.setFreelancer(freelancer);
        proposal.setPriceInr(new BigDecimal("50000.00"));
        proposal.setDurationDays(30);

        Agreement agreement = new Agreement();
        ReflectionTestUtils.setField(agreement, "id", id);
        agreement.setProposal(proposal);
        agreement.setTotalWei(new BigDecimal("200000000000000000")); // 0.2 ETH
        agreement.setStatus(AgreementStatus.FINALIZED);
        agreement.setSnapshotHash(SNAPSHOT_HASH);
        agreement.setClientWallet(CLIENT_ADDR);
        agreement.setFreelancerWallet(FREELANCER_ADDR);
        ReflectionTestUtils.setField(agreement, "createdAt", fixedClock.instant());

        return agreement;
    }

    private TransactionReceipt createCreateEscrowReceipt(String txHash, String projectIdHex, String escrowAddr,
                                                         String clientAddr, String freelancerAddr, String adminAddr,
                                                         BigInteger totalWei) {
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash(txHash);
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x64"); // 100
        receipt.setTo(FACTORY);
        receipt.setFrom(clientAddr);

        Event event = new Event(
                "EscrowCreated",
                List.of(
                        new TypeReference<Bytes32>(true) {},
                        new TypeReference<Address>(true) {},
                        new TypeReference<Address>(true) {},
                        new TypeReference<Address>(false) {},
                        new TypeReference<Address>(false) {},
                        new TypeReference<Uint256>(false) {}
                )
        );
        String topic0 = EventEncoder.encode(event);
        String topic1 = projectIdHex;
        String topic2 = "0x" + "0".repeat(24) + escrowAddr.substring(2).toLowerCase();
        String topic3 = "0x" + "0".repeat(24) + clientAddr.substring(2).toLowerCase();

        String data = "0x"
                + TypeEncoder.encode(new Address(freelancerAddr))
                + TypeEncoder.encode(new Address(adminAddr))
                + TypeEncoder.encode(new Uint256(totalWei));

        Log log = new Log();
        log.setAddress(FACTORY);
        log.setTopics(List.of(topic0, topic1, topic2, topic3));
        log.setData(data);

        receipt.setLogs(List.of(log));
        return receipt;
    }

    @Test
    void successfullyRegistersEscrowWhenVerifiedOnChain() {
        Agreement agreement = createAgreement(42L);
        when(agreementRepository.findById(42L)).thenReturn(Optional.of(agreement));
        when(agreementRepository.save(any(Agreement.class))).thenAnswer(i -> i.getArgument(0));

        // Milestones in agreement: 2 milestones, 0.1 ETH each
        Milestone m0 = new Milestone();
        m0.setPosition(0);
        m0.setAmountWei(new BigDecimal("100000000000000000"));
        Milestone m1 = new Milestone();
        m1.setPosition(1);
        m1.setAmountWei(new BigDecimal("100000000000000000"));
        when(milestoneRepository.findByAgreementIdOrderByPositionAsc(42L)).thenReturn(List.of(m0, m1));

        String expectedProjectId = OnchainProjectId.compute(42L, SNAPSHOT_HASH);
        String txHash = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        // Setup on-chain state in FakeChainReader
        chainReader.setCurrentBlockNumber(105L); // 6 confirmations
        chainReader.setupEscrow(
                ESCROW_ADDR, CLIENT_ADDR, FREELANCER_ADDR, ADMIN,
                expectedProjectId, new BigInteger("200000000000000000"),
                List.of(new BigInteger("100000000000000000"), new BigInteger("100000000000000000"))
        );
        TransactionReceipt receipt = createCreateEscrowReceipt(
                txHash, expectedProjectId, ESCROW_ADDR, CLIENT_ADDR, FREELANCER_ADDR, ADMIN,
                new BigInteger("200000000000000000")
        );
        chainReader.putReceipt(txHash, receipt);

        when(agreementService.get(42L, 1L)).thenReturn(mock(AgreementResponse.class));

        AgreementResponse res = service.registerEscrow(42L, 1L, txHash);
        assertNotNull(res);

        assertEquals(ESCROW_ADDR.toLowerCase(), agreement.getEscrowAddress());
        assertEquals(FACTORY.toLowerCase(), agreement.getFactoryAddress());
        assertEquals(expectedProjectId.toLowerCase(), agreement.getOnchainProjectId());
        assertEquals(31337L, agreement.getChainId());

        verify(transactionRepository, times(1)).save(any(BlockchainTransaction.class));
    }

    @Test
    void rejectsEscrowRegistrationWhenRevertedOnChain() {
        Agreement agreement = createAgreement(42L);
        when(agreementRepository.findById(42L)).thenReturn(Optional.of(agreement));

        String txHash = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash(txHash);
        receipt.setStatus("0x0"); // reverted
        receipt.setBlockNumber("0x64");
        chainReader.putReceipt(txHash, receipt);
        chainReader.setCurrentBlockNumber(105L);

        ApiException ex = assertThrows(ApiException.class, () -> service.registerEscrow(42L, 1L, txHash));
        assertEquals(400, ex.getStatus().value());
        assertEquals("TX_REVERTED", ex.getCode());
    }

    @Test
    void rejectsEscrowRegistrationWhenConfirmationsInsufficient() {
        Agreement agreement = createAgreement(42L);
        when(agreementRepository.findById(42L)).thenReturn(Optional.of(agreement));

        String txHash = "0xcccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash(txHash);
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x65"); // block 101
        chainReader.putReceipt(txHash, receipt);
        chainReader.setCurrentBlockNumber(100L); // current block 100 < txBlock 101 -> 0 confirmations

        ApiException ex = assertThrows(ApiException.class, () -> service.registerEscrow(42L, 1L, txHash));
        assertEquals(400, ex.getStatus().value());
        assertEquals("NOT_ENOUGH_CONFIRMATIONS", ex.getCode());
    }

    @Test
    void successfullyVerifiesFunding() {
        Agreement agreement = createAgreement(42L);
        agreement.setEscrowAddress(ESCROW_ADDR);
        when(agreementRepository.findById(42L)).thenReturn(Optional.of(agreement));
        when(agreementRepository.save(any(Agreement.class))).thenAnswer(i -> i.getArgument(0));

        Milestone m0 = new Milestone();
        m0.setPosition(0);
        m0.setWorkStatus(MilestoneWorkStatus.PENDING);
        when(milestoneRepository.findByAgreementIdOrderByPositionAsc(42L)).thenReturn(List.of(m0));

        User client = agreement.getProposal().getProject().getClient();
        when(userRepository.findById(1L)).thenReturn(Optional.of(client));

        String txHash = "0xdddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";
        TransactionReceipt receipt = new TransactionReceipt();
        receipt.setTransactionHash(txHash);
        receipt.setStatus("0x1");
        receipt.setBlockNumber("0x64");
        receipt.setTo(ESCROW_ADDR);
        receipt.setFrom(CLIENT_ADDR);

        Event fundedEvent = new Event(
                "Funded",
                List.of(
                        new TypeReference<Address>(true) {},
                        new TypeReference<Uint256>(false) {}
                )
        );
        String topic0 = EventEncoder.encode(fundedEvent);
        String topic1 = "0x" + "0".repeat(24) + CLIENT_ADDR.substring(2).toLowerCase();
        String data = "0x" + TypeEncoder.encode(new Uint256(new BigInteger("200000000000000000")));

        Log log = new Log();
        log.setAddress(ESCROW_ADDR);
        log.setTopics(List.of(topic0, topic1));
        log.setData(data);
        receipt.setLogs(List.of(log));

        chainReader.setCurrentBlockNumber(105L);
        chainReader.putReceipt(txHash, receipt);
        chainReader.setFunded(ESCROW_ADDR, true);

        when(transactionRepository.save(any(BlockchainTransaction.class))).thenAnswer(i -> {
            BlockchainTransaction tx = i.getArgument(0);
            ReflectionTestUtils.setField(tx, "id", 101L);
            return tx;
        });

        TransactionResponse response = service.verifyFunding(txHash, 42L, 1L);
        assertNotNull(response);
        assertEquals(AgreementStatus.FUNDED, agreement.getStatus());
        assertEquals(MilestoneWorkStatus.IN_PROGRESS, m0.getWorkStatus());
    }

    @Test
    void reconcilesMilestoneStateForward() {
        Agreement agreement = createAgreement(42L);
        agreement.setEscrowAddress(ESCROW_ADDR);
        agreement.setStatus(AgreementStatus.FUNDED);
        when(agreementRepository.findById(42L)).thenReturn(Optional.of(agreement));

        Milestone m0 = new Milestone();
        m0.setPosition(0);
        m0.setWorkStatus(MilestoneWorkStatus.IN_PROGRESS);
        m0.setChainStatus(MilestoneChainStatus.OPEN);

        when(milestoneRepository.findByAgreementIdOrderByPositionAsc(42L)).thenReturn(List.of(m0));

        // On-chain: milestone 0 is Released, freelancer paid 0.2 ETH
        chainReader.setupEscrow(
                ESCROW_ADDR, CLIENT_ADDR, FREELANCER_ADDR, ADMIN,
                "0x1234", new BigInteger("200000000000000000"),
                List.of(new BigInteger("200000000000000000"))
        );
        chainReader.setFunded(ESCROW_ADDR, true);
        chainReader.setCompleted(ESCROW_ADDR, true);
        chainReader.setMilestone(ESCROW_ADDR, 0, new EscrowMilestoneView(
                new BigInteger("200000000000000000"),
                1, // Released
                new BigInteger("200000000000000000"),
                BigInteger.ZERO,
                "0x0000000000000000000000000000000000000000"
        ));

        ReconcileResponse res = service.reconcile(42L, 1L, Role.CLIENT);
        assertEquals(AgreementStatus.COMPLETED, res.agreementStatus());
        assertEquals(MilestoneChainStatus.RELEASED, m0.getChainStatus());
        assertEquals(MilestoneWorkStatus.APPROVED, m0.getWorkStatus());
        assertEquals(new BigDecimal("200000000000000000"), m0.getFreelancerPaidWei());
    }
}
