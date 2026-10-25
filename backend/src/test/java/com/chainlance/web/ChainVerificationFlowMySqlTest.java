package com.chainlance.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainlance.chain.ChainReader;
import com.chainlance.chain.EscrowMilestoneView;
import com.chainlance.chain.FakeChainReader;
import com.chainlance.chain.OnchainProjectId;
import com.chainlance.config.ChainProperties;
import com.chainlance.entity.Agreement;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.User;
import com.chainlance.entity.WalletAddress;
import com.chainlance.repository.AgreementRepository;
import com.chainlance.repository.BlockchainTransactionRepository;
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
@Import(ChainVerificationFlowMySqlTest.TestChainConfig.class)
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
class ChainVerificationFlowMySqlTest {

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
            throw new IllegalStateException(
                    "Refusing to run: TEST_DB_URL must point at a separate database whose name contains '_test'.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("TEST_DB_USERNAME", "chainlance"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("TEST_DB_PASSWORD", ""));
    }

    private static final String PASSWORD = "Str0ng-pass-123";
    private static final String FACTORY = "0x1111111111111111111111111111111111111111";
    private static final String ADMIN = "0x2222222222222222222222222222222222222222";
    private static final String ESCROW = "0x5555555555555555555555555555555555555555";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private FakeChainReader fakeChainReader;
    @Autowired private ChainProperties chainProperties;
    @Autowired private AgreementRepository agreements;
    @Autowired private MilestoneRepository milestones;
    @Autowired private BlockchainTransactionRepository transactions;
    @Autowired private WalletAddressRepository walletAddresses;
    @Autowired private UserRepository users;

    private record Account(String token, long id, User user) {
    }

    private Account register(String role) throws Exception {
        String email = "cv-" + UUID.randomUUID().toString().substring(0, 12) + "@example.test";
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
    void endToEndEscrowRegistrationFundingAndReconciliation() throws Exception {
        Account client = register("CLIENT");
        Account freelancer = register("FREELANCER");

        // 1. Create project with budget 50000 INR (0.2 ETH at 250000)
        Map<String, Object> projectBody = new LinkedHashMap<>();
        projectBody.put("title", "Escrow Verification App");
        projectBody.put("description", "Two milestones.");
        projectBody.put("category", "Web Development");
        projectBody.put("budgetInr", new BigDecimal("50000.00"));
        projectBody.put("deadline", Instant.now().plus(60, ChronoUnit.DAYS).toString());

        long projectId = json(send(HttpMethod.POST, "/api/projects", client, projectBody), 201).get("id").asLong();

        // 2. Define 2-milestone plan: 50% / 50%
        List<Map<String, Object>> planItems = List.of(
                Map.of("title", "M1", "percentage", 50),
                Map.of("title", "M2", "percentage", 50)
        );
        send(HttpMethod.PUT, "/api/projects/" + projectId + "/milestone-plan", client, planItems)
                .andExpect(status().isOk());

        // 3. Publish project
        send(HttpMethod.POST, "/api/projects/" + projectId + "/publish", client, null).andExpect(status().isOk());

        // 4. Freelancer submits proposal for 50000 INR
        Map<String, Object> proposalBody = Map.of(
                "message", "Ready to start",
                "priceInr", new BigDecimal("50000.00"),
                "durationDays", 30
        );
        long proposalId = json(send(HttpMethod.POST, "/api/projects/" + projectId + "/proposals", freelancer, proposalBody), 201)
                .get("id").asLong();

        // 5. Accept offer and proposal
        JsonNode proposal = json(send(HttpMethod.GET, "/api/proposals/" + proposalId, client, null), 200);
        long offerId = proposal.get("offers").get(0).get("id").asLong();
        send(HttpMethod.POST, "/api/proposals/" + proposalId + "/offers/" + offerId + "/accept", client, null)
                .andExpect(status().isOk());

        JsonNode agreementNode = json(send(HttpMethod.POST, "/api/proposals/" + proposalId + "/accept", client, null), 201);
        long agreementId = agreementNode.get("id").asLong();
        String totalWeiStr = agreementNode.get("totalWei").asText();
        BigInteger totalWei = new BigInteger(totalWeiStr);
        assertEquals(new BigInteger("200000000000000000"), totalWei);

        // 6. Link verified wallets
        String clientWallet = "0x1122334455667788990011223344556677889900";
        String freelancerWallet = "0x9988776655443322110099887766554433221100";
        linkVerifiedWallet(client, clientWallet);
        linkVerifiedWallet(freelancer, freelancerWallet);

        send(HttpMethod.POST, "/api/agreements/" + agreementId + "/wallets", client, Map.of("address", clientWallet))
                .andExpect(status().isOk());
        send(HttpMethod.POST, "/api/agreements/" + agreementId + "/wallets", freelancer, Map.of("address", freelancerWallet))
                .andExpect(status().isOk());

        // 7. Generate milestones (two milestones of 0.1 ETH each)
        send(HttpMethod.POST, "/api/agreements/" + agreementId + "/milestones/generate", client, null)
                .andExpect(status().isOk());

        Agreement agreement = agreements.findById(agreementId).orElseThrow();
        String expectedProjectId = OnchainProjectId.compute(agreementId, agreement.getSnapshotHash());

        // 8. Setup FakeChainReader receipt for createEscrow
        String createTxHash = "0x1111222233334444555566667777888899990000aaaabbbbccccddddeeeeffff";
        fakeChainReader.setCurrentBlockNumber(200L);
        fakeChainReader.setupEscrow(
                ESCROW, clientWallet, freelancerWallet, ADMIN,
                expectedProjectId, totalWei,
                List.of(new BigInteger("100000000000000000"), new BigInteger("100000000000000000"))
        );

        TransactionReceipt createReceipt = new TransactionReceipt();
        createReceipt.setTransactionHash(createTxHash);
        createReceipt.setStatus("0x1");
        createReceipt.setBlockNumber("0xc8"); // 200
        createReceipt.setTo(FACTORY);
        createReceipt.setFrom(clientWallet);

        Event createEvent = new Event(
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
        String createTopic0 = EventEncoder.encode(createEvent);
        String createTopic1 = expectedProjectId;
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

        // Register Escrow
        JsonNode registered = json(send(HttpMethod.POST, "/api/agreements/" + agreementId + "/escrow/register", client,
                Map.of("txHash", createTxHash)), 200);
        assertEquals(ESCROW.toLowerCase(), registered.get("escrowAddress").asText());
        assertEquals(FACTORY.toLowerCase(), registered.get("factoryAddress").asText());
        assertEquals(31337, registered.get("chainId").asLong());

        // Second call is idempotent
        JsonNode secondReg = json(send(HttpMethod.POST, "/api/agreements/" + agreementId + "/escrow/register", client,
                Map.of("txHash", createTxHash)), 200);
        assertEquals(ESCROW.toLowerCase(), secondReg.get("escrowAddress").asText());

        // 9. Fund Escrow verification
        String fundTxHash = "0x2222333344445555666677778888999900001111bbbbccccddddeeeeffffaaaa";
        fakeChainReader.setCurrentBlockNumber(205L);
        fakeChainReader.setFunded(ESCROW, true);

        TransactionReceipt fundReceipt = new TransactionReceipt();
        fundReceipt.setTransactionHash(fundTxHash);
        fundReceipt.setStatus("0x1");
        fundReceipt.setBlockNumber("0xc9"); // 201
        fundReceipt.setTo(ESCROW);
        fundReceipt.setFrom(clientWallet);

        Event fundEvent = new Event(
                "Funded",
                List.of(
                        new TypeReference<Address>(true) {},
                        new TypeReference<Uint256>(false) {}
                )
        );
        String fundTopic0 = EventEncoder.encode(fundEvent);
        String fundTopic1 = "0x" + "0".repeat(24) + clientWallet.substring(2).toLowerCase();
        String fundData = "0x" + TypeEncoder.encode(new Uint256(totalWei));

        Log fundLog = new Log();
        fundLog.setAddress(ESCROW);
        fundLog.setTopics(List.of(fundTopic0, fundTopic1));
        fundLog.setData(fundData);
        fundReceipt.setLogs(List.of(fundLog));

        fakeChainReader.putReceipt(fundTxHash, fundReceipt);

        JsonNode fundVerified = json(send(HttpMethod.POST, "/api/agreements/" + agreementId + "/escrow/verify-fund", client,
                Map.of("txHash", fundTxHash)), 200);
        assertEquals("CONFIRMED", fundVerified.get("status").asText());
        assertEquals("FUND", fundVerified.get("type").asText());

        // Check agreement status moved to FUNDED
        Agreement fundedAgreement = agreements.findById(agreementId).orElseThrow();
        assertEquals(com.chainlance.entity.enums.AgreementStatus.FUNDED, fundedAgreement.getStatus());

        // Check milestone 0 moved to IN_PROGRESS
        List<Milestone> mList = milestones.findByAgreementIdOrderByPositionAsc(agreementId);
        assertEquals(com.chainlance.entity.enums.MilestoneWorkStatus.IN_PROGRESS, mList.get(0).getWorkStatus());

        // 10. Reconcile endpoint
        // Simulate milestone 0 released on chain
        fakeChainReader.setMilestone(ESCROW, 0, new EscrowMilestoneView(
                new BigInteger("100000000000000000"),
                1, // Released
                new BigInteger("100000000000000000"),
                BigInteger.ZERO,
                "0x0000000000000000000000000000000000000000"
        ));

        JsonNode reconciled = json(send(HttpMethod.POST, "/api/agreements/" + agreementId + "/reconcile", client, null), 200);
        assertEquals("FUNDED", reconciled.get("agreementStatus").asText());
        assertEquals("RELEASED", reconciled.get("milestones").get(0).get("chainStatus").asText());
        assertEquals("APPROVED", reconciled.get("milestones").get(0).get("workStatus").asText());
        assertEquals("100000000000000000", reconciled.get("milestones").get(0).get("freelancerPaidWei").asText());

        // 11. View my transactions
        JsonNode myTxs = json(send(HttpMethod.GET, "/api/transactions/mine", client, null), 200);
        assertTrue(myTxs.size() >= 2);
    }
}
