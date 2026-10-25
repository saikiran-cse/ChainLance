package com.chainlance.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainlance.chain.EscrowMilestoneView;
import com.chainlance.chain.FakeChainReader;
import com.chainlance.chain.OnchainProjectId;
import com.chainlance.config.ChainProperties;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.User;
import com.chainlance.entity.WalletAddress;
import com.chainlance.entity.enums.AgreementStatus;
import com.chainlance.entity.enums.DisputeDecision;
import com.chainlance.entity.enums.DisputeStatus;
import com.chainlance.entity.enums.ExtensionStatus;
import com.chainlance.entity.enums.MilestoneChainStatus;
import com.chainlance.entity.enums.MilestoneWorkStatus;
import com.chainlance.entity.enums.SubmissionReviewStatus;
import com.chainlance.repository.AgreementRepository;
import com.chainlance.repository.MilestoneRepository;
import com.chainlance.repository.UserRepository;
import com.chainlance.repository.WalletAddressRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(WorkflowFlowMySqlTest.TestChainConfig.class)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "chainlance.jwt.secret=test-only-secret-test-only-secret-0123456789",
        "chainlance.admin-bootstrap.email=",
        "chainlance.admin-bootstrap.password=",
        "chainlance.pricing.inr-per-eth=250000",
        "chainlance.chain.chain-id=31337",
        "chainlance.chain.factory-address=0x1111111111111111111111111111111111111111",
        "chainlance.chain.admin-address=0x2222222222222222222222222222222222222222",
        "chainlance.chain.required-confirmations=1"
})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class WorkflowFlowMySqlTest {

    @TestConfiguration
    static class TestChainConfig {
        @Bean
        @Primary
        public FakeChainReader fakeChainReader() {
            return new FakeChainReader();
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        String url = System.getenv("TEST_DB_URL");
        if (url == null || !url.toLowerCase().contains("_test")) {
            throw new IllegalStateException("Refusing to run: TEST_DB_URL must point to a test database.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USERNAME", "chainlance"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
    }

    private static final String PASSWORD = "Str0ng-pass-123";
    private static final String FACTORY = "0x1111111111111111111111111111111111111111";
    private static final String ADMIN = "0x2222222222222222222222222222222222222222";
    private static final String ESCROW = "0x6666666666666666666666666666666666666666";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FakeChainReader fakeChainReader;
    @Autowired private AgreementRepository agreements;
    @Autowired private MilestoneRepository milestones;
    @Autowired private WalletAddressRepository walletAddresses;
    @Autowired private UserRepository users;
    @Autowired private com.chainlance.security.JwtService jwtService;

    private record Account(String token, long id, User user) {}

    private Account createAdmin() {
        User adminUser = new User();
        adminUser.setEmail("admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.test");
        adminUser.setPasswordHash("hashed-password-123");
        adminUser.setDisplayName("Platform Admin");
        adminUser.setRole(com.chainlance.entity.enums.Role.ADMIN);
        User saved = users.save(adminUser);
        String token = jwtService.issue(saved.getId(), saved.getEmail(), com.chainlance.entity.enums.Role.ADMIN);
        return new Account(token, saved.getId(), saved);
    }

    private Account register(String role) throws Exception {
        String email = "wf-" + UUID.randomUUID().toString().substring(0, 12) + "@example.test";
        Map<String, Object> body = Map.of("email", email, "password", PASSWORD, "displayName", role + " Tester", "role", role);
        String text = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(text);
        long userId = json.get("user").get("id").asLong();
        User u = users.findById(userId).orElseThrow();
        return new Account(json.get("token").asText(), userId, u);
    }

    private ResultActions send(HttpMethod method, String path, Account who, Object body) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.request(method, path);
        if (who != null) {
            request.header("Authorization", "Bearer " + who.token());
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body));
        }
        return mockMvc.perform(request);
    }

    private JsonNode json(ResultActions result, int expectedStatus) throws Exception {
        String text = result.andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(text.isEmpty() ? "null" : text);
    }

    private String linkVerifiedWallet(Account account, String address) {
        WalletAddress wa = new WalletAddress();
        wa.setUser(account.user());
        wa.setAddress(address.toLowerCase());
        wa.setVerifiedAt(Instant.now());
        walletAddresses.save(wa);
        return address.toLowerCase();
    }

    @Test
    void completeEndToEndWorkflowWithSubmissionsExtensionsSettlementsDisputesAndReviews() throws Exception {
        Account client = register("CLIENT");
        Account freelancer = register("FREELANCER");
        Account admin = createAdmin();

        String clientWallet = linkVerifiedWallet(client, "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        String freelancerWallet = linkVerifiedWallet(freelancer, "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb");

        // 1. Post project 50,000 INR
        Map<String, Object> projectBody = new LinkedHashMap<>();
        projectBody.put("title", "End to end workflow project");
        projectBody.put("description", "Testing 2 milestones.");
        projectBody.put("category", "Blockchain");
        projectBody.put("budgetInr", new BigDecimal("50000.00"));
        projectBody.put("deadline", Instant.now().plus(60, ChronoUnit.DAYS).toString());

        long projectId = json(send(HttpMethod.POST, "/api/projects", client, projectBody), 201).get("id").asLong();

        // 2-milestone plan: 50% / 50%
        send(HttpMethod.PUT, "/api/projects/" + projectId + "/milestone-plan", client, List.of(
                Map.of("title", "Milestone 1", "percentage", 50),
                Map.of("title", "Milestone 2", "percentage", 50)
        )).andExpect(status().isOk());

        send(HttpMethod.POST, "/api/projects/" + projectId + "/publish", client, null).andExpect(status().isOk());

        // Proposal
        long proposalId = json(send(HttpMethod.POST, "/api/projects/" + projectId + "/proposals", freelancer, Map.of(
                "message", "I will deliver",
                "priceInr", new BigDecimal("50000.00"),
                "durationDays", 20
        )), 201).get("id").asLong();

        JsonNode proposal = json(send(HttpMethod.GET, "/api/proposals/" + proposalId, client, null), 200);
        long offerId = proposal.get("offers").get(0).get("id").asLong();
        send(HttpMethod.POST, "/api/proposals/" + proposalId + "/offers/" + offerId + "/accept", client, null)
                .andExpect(status().isOk());

        JsonNode agreementNode = json(send(HttpMethod.POST, "/api/proposals/" + proposalId + "/accept", client, null), 201);
        long agreementId = agreementNode.get("id").asLong();
        BigInteger totalWei = new BigInteger(agreementNode.get("totalWei").asText());

        // Link wallets
        send(HttpMethod.POST, "/api/agreements/" + agreementId + "/wallets", client, Map.of(
                "address", clientWallet
        )).andExpect(status().isOk());
        send(HttpMethod.POST, "/api/agreements/" + agreementId + "/wallets", freelancer, Map.of(
                "address", freelancerWallet
        )).andExpect(status().isOk());

        // Generate milestones
        JsonNode mArray = json(send(HttpMethod.POST, "/api/agreements/" + agreementId + "/milestones/generate", client, null), 200);
        assertEquals(2, mArray.size());
        long m0Id = mArray.get(0).get("id").asLong();
        long m1Id = mArray.get(1).get("id").asLong();
        BigInteger m0Wei = new BigInteger(mArray.get(0).get("amountWei").asText());
        BigInteger m1Wei = new BigInteger(mArray.get(1).get("amountWei").asText());

        // Setup fake escrow on-chain
        fakeChainReader.setCurrentBlockNumber(300L);
        fakeChainReader.setChainId(BigInteger.valueOf(31337));
        fakeChainReader.setupEscrow(ESCROW, clientWallet, freelancerWallet, ADMIN,
                OnchainProjectId.compute(agreementId, agreementNode.get("snapshotHash").asText()),
                totalWei,
                List.of(m0Wei, m1Wei)
        );

        // Register Escrow
        String createTxHash = "0x" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        TransactionReceipt createReceipt = new TransactionReceipt();
        createReceipt.setTransactionHash(createTxHash);
        createReceipt.setStatus("0x1");
        createReceipt.setBlockNumber(org.web3j.utils.Numeric.encodeQuantity(BigInteger.valueOf(250)));
        createReceipt.setFrom(clientWallet);
        createReceipt.setTo(FACTORY);

        Event escrowCreatedEvent = new Event("EscrowCreated", List.of(
                new TypeReference<Bytes32>(true) {},
                new TypeReference<Address>(true) {},
                new TypeReference<Address>(true) {},
                new TypeReference<Address>(false) {},
                new TypeReference<Address>(false) {},
                new TypeReference<Uint256>(false) {}
        ));
        String createTopic0 = EventEncoder.encode(escrowCreatedEvent);
        String createTopic1 = OnchainProjectId.compute(agreementId, agreementNode.get("snapshotHash").asText());
        String createTopic2 = "0x" + "0".repeat(24) + ESCROW.substring(2).toLowerCase();
        String createTopic3 = "0x" + "0".repeat(24) + clientWallet.substring(2).toLowerCase();
        String createData = "0x"
                + TypeEncoder.encode(new Address(freelancerWallet))
                + TypeEncoder.encode(new Address(ADMIN))
                + TypeEncoder.encode(new Uint256(totalWei));

        Log createLog = new Log();
        createLog.setAddress(FACTORY);
        createLog.setTopics(List.of(createTopic0, createTopic1, createTopic2, createTopic3));
        createLog.setData(createData);
        createReceipt.setLogs(List.of(createLog));
        fakeChainReader.putReceipt(createTxHash, createReceipt);

        send(HttpMethod.POST, "/api/agreements/" + agreementId + "/escrow/register", client, Map.of("txHash", createTxHash))
                .andExpect(status().isOk());

        // Fund Escrow
        fakeChainReader.setFunded(ESCROW, true);
        String fundTxHash = "0x" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        TransactionReceipt fundReceipt = new TransactionReceipt();
        fundReceipt.setTransactionHash(fundTxHash);
        fundReceipt.setStatus("0x1");
        fundReceipt.setBlockNumber(org.web3j.utils.Numeric.encodeQuantity(BigInteger.valueOf(255)));
        fundReceipt.setFrom(clientWallet);
        fundReceipt.setTo(ESCROW);

        Event fundedEvent = new Event("Funded", List.of(
                new TypeReference<Address>(true) {},
                new TypeReference<Uint256>(false) {}
        ));
        Log fundLog = new Log();
        fundLog.setAddress(ESCROW);
        fundLog.setTopics(List.of(
                EventEncoder.encode(fundedEvent),
                "0x" + "0".repeat(24) + clientWallet.substring(2).toLowerCase()
        ));
        fundLog.setData("0x" + TypeEncoder.encode(new Uint256(totalWei)));
        fundReceipt.setLogs(List.of(fundLog));
        fakeChainReader.putReceipt(fundTxHash, fundReceipt);

        send(HttpMethod.POST, "/api/agreements/" + agreementId + "/escrow/verify-fund", client, Map.of("txHash", fundTxHash))
                .andExpect(status().isOk());

        Agreement ag = agreements.findById(agreementId).orElseThrow();
        assertEquals(AgreementStatus.FUNDED, ag.getStatus());

        Milestone m0 = milestones.findById(m0Id).orElseThrow();
        assertEquals(MilestoneWorkStatus.IN_PROGRESS, m0.getWorkStatus());

        // =================================================================
        // PHASE 4 FEATURE 1: SUBMISSIONS & REVISIONS
        // =================================================================
        // 1a. Freelancer submits work for Milestone 0
        JsonNode sub1 = json(send(HttpMethod.POST, "/api/milestones/" + m0Id + "/submissions", freelancer, Map.of(
                "description", "Initial deliverable for M0",
                "links", "https://github.com/chainlance/demo"
        )), 200);
        long sub1Id = sub1.get("id").asLong();
        assertEquals("PENDING", sub1.get("reviewStatus").asText());

        Milestone m0AfterSub = milestones.findById(m0Id).orElseThrow();
        assertEquals(MilestoneWorkStatus.SUBMITTED, m0AfterSub.getWorkStatus());

        // 1b. Client requests revision
        JsonNode revResp = json(send(HttpMethod.POST, "/api/submissions/" + sub1Id + "/request-revision", client, Map.of(
                "reason", "Please fix unit tests"
        )), 200);
        assertEquals("REVISION_REQUESTED", revResp.get("reviewStatus").asText());
        assertEquals("Please fix unit tests", revResp.get("revisionReason").asText());

        Milestone m0AfterRev = milestones.findById(m0Id).orElseThrow();
        assertEquals(MilestoneWorkStatus.REVISION_REQUESTED, m0AfterRev.getWorkStatus());

        // 1c. Freelancer resubmits work
        JsonNode sub2 = json(send(HttpMethod.POST, "/api/milestones/" + m0Id + "/submissions", freelancer, Map.of(
                "description", "Fixed unit tests and improved docs",
                "links", "https://github.com/chainlance/demo/commit/fixed"
        )), 200);
        long sub2Id = sub2.get("id").asLong();
        assertEquals("PENDING", sub2.get("reviewStatus").asText());

        // 1d. Client approves submission with on-chain release
        String approveTxHash = "0x" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        TransactionReceipt approveReceipt = new TransactionReceipt();
        approveReceipt.setTransactionHash(approveTxHash);
        approveReceipt.setStatus("0x1");
        approveReceipt.setBlockNumber(org.web3j.utils.Numeric.encodeQuantity(BigInteger.valueOf(260)));
        approveReceipt.setFrom(clientWallet);
        approveReceipt.setTo(ESCROW);

        Event releasedEvent = new Event("MilestoneReleased", List.of(
                new TypeReference<Uint256>(true) {},
                new TypeReference<Address>(true) {},
                new TypeReference<Uint256>(false) {}
        ));
        Log releasedLog = new Log();
        releasedLog.setAddress(ESCROW);
        releasedLog.setTopics(List.of(
                EventEncoder.encode(releasedEvent),
                "0x" + "0".repeat(64),
                "0x" + "0".repeat(24) + freelancerWallet.substring(2).toLowerCase()
        ));
        releasedLog.setData("0x" + TypeEncoder.encode(new Uint256(m0Wei)));
        approveReceipt.setLogs(List.of(releasedLog));
        fakeChainReader.putReceipt(approveTxHash, approveReceipt);

        JsonNode approveResp = json(send(HttpMethod.POST, "/api/submissions/" + sub2Id + "/approve", client, Map.of(
                "txHash", approveTxHash
        )), 200);
        assertEquals("APPROVED", approveResp.get("reviewStatus").asText());

        Milestone m0Approved = milestones.findById(m0Id).orElseThrow();
        assertEquals(MilestoneWorkStatus.APPROVED, m0Approved.getWorkStatus());
        assertEquals(MilestoneChainStatus.RELEASED, m0Approved.getChainStatus());
        assertEquals(m0Wei, m0Approved.getFreelancerPaidWei().toBigInteger());

        // Milestone 1 should automatically advance to IN_PROGRESS!
        Milestone m1 = milestones.findById(m1Id).orElseThrow();
        assertEquals(MilestoneWorkStatus.IN_PROGRESS, m1.getWorkStatus());

        // =================================================================
        // PHASE 4 FEATURE 2: DEADLINE EXTENSIONS
        // =================================================================
        Instant extendedDeadline = m1.getEffectiveDeadline().plus(14, ChronoUnit.DAYS);
        JsonNode extNode = json(send(HttpMethod.POST, "/api/milestones/" + m1Id + "/extensions", freelancer, Map.of(
                "requestedDeadline", extendedDeadline.toString(),
                "reason", "Need 2 more weeks for complex UI integration"
        )), 200);
        long extId = extNode.get("id").asLong();
        assertEquals("PENDING", extNode.get("status").asText());

        // Duplicate pending extension request must fail (409)
        send(HttpMethod.POST, "/api/milestones/" + m1Id + "/extensions", freelancer, Map.of(
                "requestedDeadline", extendedDeadline.plus(5, ChronoUnit.DAYS).toString(),
                "reason", "Another extension"
        )).andExpect(status().isConflict());

        // Client approves extension
        JsonNode approvedExt = json(send(HttpMethod.POST, "/api/extensions/" + extId + "/approve", client, Map.of(
                "note", "Approved, please finish on time"
        )), 200);
        assertEquals("APPROVED", approvedExt.get("status").asText());

        Milestone m1Extended = milestones.findById(m1Id).orElseThrow();
        assertEquals(extendedDeadline.truncatedTo(ChronoUnit.MILLIS), m1Extended.getEffectiveDeadline().truncatedTo(ChronoUnit.MILLIS));

        // =================================================================
        // PHASE 4 FEATURE 3: SETTLEMENTS
        // =================================================================
        // Freelancer proposes 50/50 split of Milestone 1
        BigInteger fSplitWei = m1Wei.divide(BigInteger.TWO);
        BigInteger cSplitWei = m1Wei.subtract(fSplitWei);

        JsonNode propNode = json(send(HttpMethod.POST, "/api/milestones/" + m1Id + "/settlements", freelancer, Map.of(
                "freelancerWei", fSplitWei.toString(),
                "clientWei", cSplitWei.toString(),
                "reason", "Mutual settlement proposal"
        )), 200);
        long settlementId = propNode.get("id").asLong();

        // Withdraw test: proposer can withdraw, then re-propose
        send(HttpMethod.POST, "/api/settlements/" + settlementId + "/withdraw", freelancer, null)
                .andExpect(status().isOk());

        // Re-propose
        JsonNode rePropNode = json(send(HttpMethod.POST, "/api/milestones/" + m1Id + "/settlements", freelancer, Map.of(
                "freelancerWei", fSplitWei.toString(),
                "clientWei", cSplitWei.toString(),
                "reason", "Settlement proposal renewed"
        )), 200);
        long activeSettlementId = rePropNode.get("id").asLong();

        // Proposer cannot accept own proposal
        send(HttpMethod.POST, "/api/settlements/" + activeSettlementId + "/accept", freelancer, Map.of(
                "txHash", "0x123"
        )).andExpect(status().isBadRequest());

        // =================================================================
        // PHASE 4 FEATURE 4: DISPUTES & ADMIN RESOLUTION
        // =================================================================
        // Client opens dispute on Milestone 1 instead of accepting settlement
        String disputeTxHash = "0x" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        TransactionReceipt disputeReceipt = new TransactionReceipt();
        disputeReceipt.setTransactionHash(disputeTxHash);
        disputeReceipt.setStatus("0x1");
        disputeReceipt.setBlockNumber(org.web3j.utils.Numeric.encodeQuantity(BigInteger.valueOf(270)));
        disputeReceipt.setFrom(clientWallet);
        disputeReceipt.setTo(ESCROW);

        Event disputeOpenedEvent = new Event("DisputeOpened", List.of(
                new TypeReference<Uint256>(true) {},
                new TypeReference<Address>(true) {},
                new TypeReference<Bytes32>(false) {}
        ));
        Log disputeLog = new Log();
        disputeLog.setAddress(ESCROW);
        disputeLog.setTopics(List.of(
                EventEncoder.encode(disputeOpenedEvent),
                "0x" + "0".repeat(63) + "1",
                "0x" + "0".repeat(24) + clientWallet.substring(2).toLowerCase()
        ));
        String reasonHash = "0x" + "aa".repeat(32);
        disputeLog.setData(reasonHash);
        disputeReceipt.setLogs(List.of(disputeLog));
        fakeChainReader.putReceipt(disputeTxHash, disputeReceipt);

        JsonNode disputeNode = json(send(HttpMethod.POST, "/api/milestones/" + m1Id + "/disputes", client, Map.of(
                "reason", "Deliverable was incomplete and communication failed",
                "txHash", disputeTxHash
        )), 200);
        long disputeId = disputeNode.get("id").asLong();
        assertEquals("OPEN", disputeNode.get("status").asText());

        Milestone m1Disputed = milestones.findById(m1Id).orElseThrow();
        assertEquals(MilestoneChainStatus.DISPUTED, m1Disputed.getChainStatus());

        // Freelancer replies with evidence
        JsonNode replyNode = json(send(HttpMethod.POST, "/api/disputes/" + disputeId + "/responses", freelancer, Map.of(
                "response", "I delivered all code per the acceptance criteria",
                "evidenceLinks", "https://github.com/chainlance/demo/pull/1"
        )), 200);
        assertNotNull(replyNode.get("id"));

        // Admin lists open disputes
        JsonNode adminList = json(send(HttpMethod.GET, "/api/admin/disputes", admin, null), 200);
        assertTrue(adminList.get("totalElements").asLong() >= 1);

        // Admin decides SPLIT decision
        JsonNode decisionNode = json(send(HttpMethod.POST, "/api/admin/disputes/" + disputeId + "/decision", admin, Map.of(
                "decision", "SPLIT",
                "freelancerWei", fSplitWei.toString(),
                "clientWei", cSplitWei.toString(),
                "explanation", "Split 50/50 based on submitted evidence"
        )), 200);
        assertEquals("DECIDED", decisionNode.get("status").asText());
        assertEquals("SPLIT", decisionNode.get("adminDecision").asText());

        // Admin signs on-chain resolveDispute transaction
        String resolveTxHash = "0x" + UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        TransactionReceipt resolveReceipt = new TransactionReceipt();
        resolveReceipt.setTransactionHash(resolveTxHash);
        resolveReceipt.setStatus("0x1");
        resolveReceipt.setBlockNumber(org.web3j.utils.Numeric.encodeQuantity(BigInteger.valueOf(280)));
        resolveReceipt.setFrom(ADMIN);
        resolveReceipt.setTo(ESCROW);

        Event disputeResolvedEvent = new Event("DisputeResolved", List.of(
                new TypeReference<Uint256>(true) {},
                new TypeReference<Address>(true) {},
                new TypeReference<Uint256>(false) {},
                new TypeReference<Uint256>(false) {},
                new TypeReference<Bytes32>(false) {}
        ));
        Log resolveLog = new Log();
        resolveLog.setAddress(ESCROW);
        resolveLog.setTopics(List.of(
                EventEncoder.encode(disputeResolvedEvent),
                "0x" + "0".repeat(63) + "1",
                "0x" + "0".repeat(24) + ADMIN.substring(2).toLowerCase()
        ));
        String resHash = "0x" + "bb".repeat(32);
        resolveLog.setData("0x"
                + TypeEncoder.encode(new Uint256(fSplitWei))
                + TypeEncoder.encode(new Uint256(cSplitWei))
                + "bb".repeat(32));
        resolveReceipt.setLogs(List.of(resolveLog));
        fakeChainReader.putReceipt(resolveTxHash, resolveReceipt);

        JsonNode resolveNode = json(send(HttpMethod.POST, "/api/admin/disputes/" + disputeId + "/resolution-tx", admin, Map.of(
                "txHash", resolveTxHash
        )), 200);
        assertEquals("RESOLVED", resolveNode.get("status").asText());

        Milestone m1Resolved = milestones.findById(m1Id).orElseThrow();
        assertEquals(MilestoneChainStatus.DISPUTE_RESOLVED, m1Resolved.getChainStatus());
        assertEquals(MilestoneWorkStatus.CLOSED, m1Resolved.getWorkStatus());

        // Since ALL milestones are resolved (M0 released, M1 dispute_resolved), Agreement must now be COMPLETED!
        Agreement agFinal = agreements.findById(agreementId).orElseThrow();
        assertEquals(AgreementStatus.COMPLETED, agFinal.getStatus());

        // =================================================================
        // PHASE 4 FEATURE 5: REVIEWS
        // =================================================================
        // Client reviews freelancer
        JsonNode clientReview = json(send(HttpMethod.POST, "/api/agreements/" + agreementId + "/reviews", client, Map.of(
                "rating", 4,
                "comment", "Good overall work despite the dispute resolution"
        )), 200);
        assertNotNull(clientReview.get("id"));
        assertEquals(4, clientReview.get("rating").asInt());

        // Duplicate review by same user fails (400)
        send(HttpMethod.POST, "/api/agreements/" + agreementId + "/reviews", client, Map.of(
                "rating", 5,
                "comment", "Second review"
        )).andExpect(status().isBadRequest());

        // Freelancer reviews client
        JsonNode freelancerReview = json(send(HttpMethod.POST, "/api/agreements/" + agreementId + "/reviews", freelancer, Map.of(
                "rating", 5,
                "comment", "Prompt payments and clear communication"
        )), 200);
        assertNotNull(freelancerReview.get("id"));
        assertEquals(5, freelancerReview.get("rating").asInt());

        // Query freelancer's reviews (authenticated user)
        JsonNode flReviews = json(send(HttpMethod.GET, "/api/users/" + freelancer.id() + "/reviews", client, null), 200);
        assertEquals(1, flReviews.get("totalReviews").asLong());
        assertEquals(4.0, flReviews.get("averageRating").asDouble(), 0.01);

        // Query client's reviews (authenticated user)
        JsonNode clReviews = json(send(HttpMethod.GET, "/api/users/" + client.id() + "/reviews", freelancer, null), 200);
        assertEquals(1, clReviews.get("totalReviews").asLong());
        assertEquals(5.0, clReviews.get("averageRating").asDouble(), 0.01);
    }
}
