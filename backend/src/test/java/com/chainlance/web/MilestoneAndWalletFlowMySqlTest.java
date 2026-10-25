package com.chainlance.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainlance.entity.User;
import com.chainlance.entity.WalletAddress;
import com.chainlance.repository.AgreementRepository;
import com.chainlance.repository.MilestoneRepository;
import com.chainlance.repository.ProjectMilestonePlanRepository;
import com.chainlance.repository.ProjectRepository;
import com.chainlance.repository.ProposalRepository;
import com.chainlance.repository.UserRepository;
import com.chainlance.repository.WalletAddressRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
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

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
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
class MilestoneAndWalletFlowMySqlTest {

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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProjectRepository projects;
    @Autowired private ProposalRepository proposals;
    @Autowired private AgreementRepository agreements;
    @Autowired private MilestoneRepository milestones;
    @Autowired private ProjectMilestonePlanRepository plans;
    @Autowired private WalletAddressRepository walletAddresses;
    @Autowired private UserRepository users;
    @PersistenceContext private EntityManager em;

    private record Account(String token, long id, User user) {
    }

    private Account register(String role) throws Exception {
        String email = "mw-" + UUID.randomUUID().toString().substring(0, 12) + "@example.test";
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
    void chainConfigEndpointReturnsConfiguredProperties() throws Exception {
        Account client = register("CLIENT");

        // Unauthenticated access fails
        mockMvc.perform(MockMvcRequestBuilders.get("/api/chain/config"))
                .andExpect(status().isUnauthorized());

        // Authenticated access succeeds
        JsonNode config = json(send(HttpMethod.GET, "/api/chain/config", client, null), 200);
        assertEquals(31337, config.get("chainId").asLong());
        assertEquals("0x1111111111111111111111111111111111111111", config.get("factoryAddress").asText());
        assertEquals("0x2222222222222222222222222222222222222222", config.get("adminAddress").asText());
        assertEquals(1, config.get("requiredConfirmations").asInt());
        assertEquals("250000", config.get("inrPerEth").asText());
    }

    @Test
    void endToEndMilestonePlanAndWalletLinkingAndGeneration() throws Exception {
        Account client = register("CLIENT");
        Account otherClient = register("CLIENT");
        Account freelancer = register("FREELANCER");
        Account stranger = register("FREELANCER");

        // 1. Create a draft project
        Map<String, Object> projectBody = new LinkedHashMap<>();
        projectBody.put("title", "Full Marketplace App");
        projectBody.put("description", "Needs 3 milestones to build.");
        projectBody.put("category", "Web Development");
        projectBody.put("budgetInr", new BigDecimal("100000.00"));
        projectBody.put("deadline", Instant.now().plus(60, ChronoUnit.DAYS).toString());

        long projectId = json(send(HttpMethod.POST, "/api/projects", client, projectBody), 201).get("id").asLong();

        // 2. Client defines milestone plan on draft project
        List<Map<String, Object>> planItems = List.of(
                Map.of("title", "Frontend Setup", "description", "UI layout and wireframes", "percentage", 30),
                Map.of("title", "Backend API", "description", "Spring boot services", "percentage", 40),
                Map.of("title", "Smart Contracts", "description", "Escrow integration", "percentage", 30)
        );

        // Percentage != 100 rejected
        List<Map<String, Object>> badPlan = List.of(
                Map.of("title", "M1", "percentage", 50),
                Map.of("title", "M2", "percentage", 40)
        );
        send(HttpMethod.PUT, "/api/projects/" + projectId + "/milestone-plan", client, badPlan)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PERCENTAGE_SUM_INVALID"));

        // Another client cannot update plan (404)
        send(HttpMethod.PUT, "/api/projects/" + projectId + "/milestone-plan", otherClient, planItems)
                .andExpect(status().isNotFound());

        // Freelancer cannot update plan (403)
        send(HttpMethod.PUT, "/api/projects/" + projectId + "/milestone-plan", stranger, planItems)
                .andExpect(status().isForbidden());

        // Valid plan accepted
        JsonNode savedPlan = json(send(HttpMethod.PUT, "/api/projects/" + projectId + "/milestone-plan", client, planItems), 200);
        assertEquals(3, savedPlan.size());
        assertEquals(0, savedPlan.get(0).get("position").asInt());
        assertEquals(1, savedPlan.get(1).get("position").asInt());
        assertEquals(2, savedPlan.get(2).get("position").asInt());

        // View plan via GET
        JsonNode fetchedPlan = json(send(HttpMethod.GET, "/api/projects/" + projectId + "/milestone-plan", client, null), 200);
        assertEquals(3, fetchedPlan.size());

        // 3. Publish project
        send(HttpMethod.POST, "/api/projects/" + projectId + "/publish", client, null).andExpect(status().isOk());

        // 4. Freelancer submits proposal
        Map<String, Object> proposalBody = Map.of(
                "message", "I can build this in 45 days.",
                "priceInr", new BigDecimal("90000.00"), // negotiated price: 90000 instead of 100000 budget
                "durationDays", 45
        );
        long proposalId = json(send(HttpMethod.POST, "/api/projects/" + projectId + "/proposals", freelancer, proposalBody), 201)
                .get("id").asLong();

        // Trying to update milestone plan now fails because proposals exist (409)
        send(HttpMethod.PUT, "/api/projects/" + projectId + "/milestone-plan", client, planItems)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROPOSALS_EXIST"));

        // 5. Client accepts offer & proposal to create agreement
        // The freelancer's first proposal auto-creates offer #1. Client accepts offer #1:
        JsonNode proposal = json(send(HttpMethod.GET, "/api/proposals/" + proposalId, client, null), 200);
        long offerId = proposal.get("offers").get(0).get("id").asLong();
        send(HttpMethod.POST, "/api/proposals/" + proposalId + "/offers/" + offerId + "/accept", client, null)
                .andExpect(status().isOk());

        // Client accepts proposal to create agreement
        JsonNode agreement = json(send(HttpMethod.POST, "/api/proposals/" + proposalId + "/accept", client, null), 201);
        long agreementId = agreement.get("id").asLong();
        String totalWei = agreement.get("totalWei").asText();
        assertNotNull(totalWei);

        // 6. Wallet linking
        String clientWalletAddr = "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String freelancerWalletAddr = "0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        String unverifiedAddr = "0xcccccccccccccccccccccccccccccccccccccccc";

        // Try linking unverified wallet -> 400
        send(HttpMethod.POST, "/api/agreements/" + agreementId + "/wallets", client, Map.of("address", unverifiedAddr))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_LINKED"));

        // Link verified wallets
        linkVerifiedWallet(client, clientWalletAddr);
        linkVerifiedWallet(freelancer, freelancerWalletAddr);

        // Stranger cannot link wallet (404)
        send(HttpMethod.POST, "/api/agreements/" + agreementId + "/wallets", stranger, Map.of("address", clientWalletAddr))
                .andExpect(status().isNotFound());

        // Client links wallet
        JsonNode updatedClient = json(send(HttpMethod.POST, "/api/agreements/" + agreementId + "/wallets", client,
                Map.of("address", clientWalletAddr)), 200);
        assertEquals(clientWalletAddr.toLowerCase(), updatedClient.get("clientWallet").asText());
        assertNotNull(updatedClient.get("clientFinalizedAt"));

        // Freelancer links wallet
        JsonNode updatedFreelancer = json(send(HttpMethod.POST, "/api/agreements/" + agreementId + "/wallets", freelancer,
                Map.of("address", freelancerWalletAddr)), 200);
        assertEquals(freelancerWalletAddr.toLowerCase(), updatedFreelancer.get("freelancerWallet").asText());
        assertNotNull(updatedFreelancer.get("freelancerFinalizedAt"));

        // 7. Milestone generation
        // Freelancer calling generate -> 403 (role check)
        send(HttpMethod.POST, "/api/agreements/" + agreementId + "/milestones/generate", freelancer, null)
                .andExpect(status().isForbidden());

        // Other client calling generate -> 404 (not owner of agreement)
        send(HttpMethod.POST, "/api/agreements/" + agreementId + "/milestones/generate", otherClient, null)
                .andExpect(status().isNotFound());

        // Client generates milestones
        JsonNode generatedMilestones = json(send(HttpMethod.POST, "/api/agreements/" + agreementId + "/milestones/generate", client, null), 200);
        assertEquals(3, generatedMilestones.size());

        // Verify positions 0, 1, 2
        assertEquals(0, generatedMilestones.get(0).get("position").asInt());
        assertEquals(1, generatedMilestones.get(1).get("position").asInt());
        assertEquals(2, generatedMilestones.get(2).get("position").asInt());

        // Verify total wei matches sum of milestone wei exactly (zero loss)
        BigDecimal sumWei = BigDecimal.ZERO;
        for (JsonNode m : generatedMilestones) {
            assertEquals("OPEN", m.get("chainStatus").asText());
            assertEquals("PENDING", m.get("workStatus").asText());
            assertEquals("0", m.get("freelancerPaidWei").asText());
            assertEquals("0", m.get("clientRefundedWei").asText());
            BigDecimal wei = new BigDecimal(m.get("amountWei").asText());
            assertTrue(wei.signum() > 0);
            sumWei = sumWei.add(wei);
        }
        assertEquals(new BigDecimal(totalWei), sumWei);

        // 8. Idempotence: calling generate again returns the same milestones without creating duplicates
        JsonNode secondCall = json(send(HttpMethod.POST, "/api/agreements/" + agreementId + "/milestones/generate", client, null), 200);
        assertEquals(3, secondCall.size());
        assertEquals(generatedMilestones.get(0).get("id").asLong(), secondCall.get(0).get("id").asLong());

        // 9. Fetch milestones as freelancer & client
        JsonNode freelancerView = json(send(HttpMethod.GET, "/api/agreements/" + agreementId + "/milestones", freelancer, null), 200);
        assertEquals(3, freelancerView.size());

        JsonNode clientView = json(send(HttpMethod.GET, "/api/agreements/" + agreementId + "/milestones", client, null), 200);
        assertEquals(3, clientView.size());

        // Stranger gets 404
        send(HttpMethod.GET, "/api/agreements/" + agreementId + "/milestones", stranger, null)
                .andExpect(status().isNotFound());

        // 10. Fetch single milestone
        long m0Id = generatedMilestones.get(0).get("id").asLong();
        JsonNode singleM = json(send(HttpMethod.GET, "/api/milestones/" + m0Id, client, null), 200);
        assertEquals(m0Id, singleM.get("id").asLong());
        assertEquals(0, singleM.get("position").asInt());

        send(HttpMethod.GET, "/api/milestones/" + m0Id, stranger, null)
                .andExpect(status().isNotFound());
    }
}
