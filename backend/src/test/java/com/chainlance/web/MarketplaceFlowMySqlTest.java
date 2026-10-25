package com.chainlance.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chainlance.entity.Agreement;
import com.chainlance.entity.Project;
import com.chainlance.entity.Proposal;
import com.chainlance.entity.enums.AgreementStatus;
import com.chainlance.entity.enums.OfferStatus;
import com.chainlance.entity.enums.ProjectStatus;
import com.chainlance.entity.enums.ProposalStatus;
import com.chainlance.repository.AgreementRepository;
import com.chainlance.repository.NegotiationOfferRepository;
import com.chainlance.repository.ProjectRepository;
import com.chainlance.repository.ProposalRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
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

/**
 * Stage 4 end-to-end tests: projects, proposals, price negotiation and agreement snapshots, through the real HTTP
 * layer, real security, real JWTs and a real MySQL test database. Skipped unless TEST_DB_URL is set (and it must
 * contain "_test"). Every test is rolled back.
 *
 * Note: each test runs inside one test transaction, so "nothing was changed after a refusal" is checked by reading
 * the state back. The service also performs every check BEFORE it changes anything.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "chainlance.jwt.secret=test-only-secret-test-only-secret-0123456789",
        "chainlance.admin-bootstrap.email=",
        "chainlance.admin-bootstrap.password=",
        "chainlance.pricing.inr-per-eth=250000"
})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class MarketplaceFlowMySqlTest {

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
    @Autowired private NegotiationOfferRepository offers;
    @PersistenceContext private EntityManager em;

    // =================================================================== helpers

    private record Account(String token, long id) {
    }

    private record Deal(Account client, Account freelancer, long projectId, long proposalId) {
    }

    private Account register(String role) throws Exception {
        String email = "m-" + UUID.randomUUID().toString().substring(0, 12) + "@example.test";
        Map<String, Object> body = Map.of("email", email, "password", PASSWORD, "displayName", role + " Tester", "role", role);
        String text = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(text);
        return new Account(json.get("token").asText(), json.get("user").get("id").asLong());
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

    /** Checks the HTTP status and returns the JSON body. */
    private JsonNode json(ResultActions result, int expectedStatus) throws Exception {
        String text = result.andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(text.isEmpty() ? "null" : text);
    }

    private void expectError(ResultActions result, int expectedStatus, String code) throws Exception {
        result.andExpect(status().is(expectedStatus)).andExpect(jsonPath("$.code").value(code));
    }

    private Map<String, Object> projectBody(String budget) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Build an online store");
        body.put("description", "A small e-commerce site with a cart and checkout.");
        body.put("category", "Web Development");
        body.put("skills", "React, Spring Boot");
        body.put("budgetInr", new BigDecimal(budget));
        body.put("deadline", Instant.now().plus(60, ChronoUnit.DAYS).toString());
        return body;
    }

    private Map<String, Object> proposalBody(String price, int days) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", "I have built several stores like this.");
        body.put("priceInr", new BigDecimal(price));
        body.put("durationDays", days);
        return body;
    }

    private Map<String, Object> offerBody(String price, int days) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("priceInr", new BigDecimal(price));
        body.put("durationDays", days);
        body.put("note", "Let's meet in the middle.");
        return body;
    }

    private long openProject(Account client, String budget) throws Exception {
        long id = json(send(HttpMethod.POST, "/api/projects", client, projectBody(budget)), 201).get("id").asLong();
        json(send(HttpMethod.POST, "/api/projects/" + id + "/publish", client, null), 200);
        return id;
    }

    private long propose(Account freelancer, long projectId, String price, int days) throws Exception {
        return json(send(HttpMethod.POST, "/api/projects/" + projectId + "/proposals", freelancer, proposalBody(price, days)), 201)
                .get("id").asLong();
    }

    /** A fresh client and freelancer, an OPEN project with budget 60000 and a freelancer proposal of 58000 for 30 days. */
    private Deal openDeal() throws Exception {
        Account client = register("CLIENT");
        Account freelancer = register("FREELANCER");
        long projectId = openProject(client, "60000");
        long proposalId = propose(freelancer, projectId, "58000", 30);
        return new Deal(client, freelancer, projectId, proposalId);
    }

    private long pendingOfferId(Account who, long proposalId) throws Exception {
        JsonNode history = json(send(HttpMethod.GET, "/api/proposals/" + proposalId + "/offers", who, null), 200);
        for (JsonNode offer : history) {
            if ("PENDING".equals(offer.get("status").asText())) {
                return offer.get("id").asLong();
            }
        }
        throw new AssertionError("no pending offer on proposal " + proposalId);
    }

    /** The client accepts the freelancer's first offer, so the terms are agreed at 58000. */
    private void clientAcceptsFirstOffer(Deal d) throws Exception {
        long offerId = pendingOfferId(d.client(), d.proposalId());
        JsonNode accepted = json(send(HttpMethod.POST, "/api/proposals/" + d.proposalId() + "/offers/" + offerId + "/accept", d.client(), null), 200);
        assertEquals("ACCEPTED", accepted.get("status").asText());
    }

    private static boolean containsId(JsonNode array, long id) {
        for (JsonNode item : array) {
            if (item.get("id").asLong() == id) {
                return true;
            }
        }
        return false;
    }

    private static BigDecimal decimal(JsonNode node) {
        return new BigDecimal(node.asText());
    }

    // =================================================================== A. projects

    @Test
    void clientCreatesADraftProjectThenPublishesIt() throws Exception {
        Account client = register("CLIENT");
        Account freelancer = register("FREELANCER");

        // the client tries to smuggle in another owner and a status: both are ignored
        Map<String, Object> body = projectBody("50000");
        body.put("clientId", 999999);
        body.put("status", "CLOSED");
        JsonNode created = json(send(HttpMethod.POST, "/api/projects", client, body), 201);
        long id = created.get("id").asLong();
        assertEquals("DRAFT", created.get("status").asText());
        assertEquals(client.id(), created.get("client").get("id").asLong());
        assertFalse(created.get("client").has("email"), "other users' emails must never be exposed");

        expectError(send(HttpMethod.GET, "/api/projects/" + id, freelancer, null), 404, "PROJECT_NOT_FOUND"); // drafts are private

        assertEquals("OPEN", json(send(HttpMethod.POST, "/api/projects/" + id + "/publish", client, null), 200).get("status").asText());
        send(HttpMethod.GET, "/api/projects/" + id, freelancer, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.client.email").doesNotExist());
        assertTrue(containsId(json(send(HttpMethod.GET, "/api/projects?size=50", freelancer, null), 200).get("content"), id));
        assertTrue(containsId(json(send(HttpMethod.GET, "/api/projects/mine", client, null), 200), id));
    }

    @Test
    void freelancerCannotCreateOrListOwnedProjects() throws Exception {
        Account freelancer = register("FREELANCER");
        expectError(send(HttpMethod.POST, "/api/projects", freelancer, projectBody("50000")), 403, "FORBIDDEN");
        expectError(send(HttpMethod.GET, "/api/projects/mine", freelancer, null), 403, "FORBIDDEN");
    }

    @Test
    void requestsWithoutALoginAreRejected() throws Exception {
        send(HttpMethod.GET, "/api/projects", null, null).andExpect(status().isUnauthorized());
        send(HttpMethod.POST, "/api/projects", null, projectBody("50000")).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherClientCannotEditCloseOrCancelTheProject() throws Exception {
        Account owner = register("CLIENT");
        Account other = register("CLIENT");
        long id = openProject(owner, "50000");

        expectError(send(HttpMethod.PUT, "/api/projects/" + id, other, projectBody("50000")), 403, "NOT_PROJECT_OWNER");
        expectError(send(HttpMethod.POST, "/api/projects/" + id + "/close", other, null), 403, "NOT_PROJECT_OWNER");
        expectError(send(HttpMethod.POST, "/api/projects/" + id + "/cancel", other, null), 403, "NOT_PROJECT_OWNER");
        assertEquals(ProjectStatus.OPEN, projects.findById(id).orElseThrow().getStatus());

        assertEquals("CLOSED", json(send(HttpMethod.POST, "/api/projects/" + id + "/close", owner, null), 200).get("status").asText());
    }

    @Test
    void invalidBudgetsMissingFieldsAndPastDeadlinesAreRejected() throws Exception {
        Account client = register("CLIENT");
        for (String badBudget : new String[]{"0", "-10", "100.005"}) {
            expectError(send(HttpMethod.POST, "/api/projects", client, projectBody(badBudget)), 400, "VALIDATION_FAILED");
        }
        Map<String, Object> noTitle = projectBody("50000");
        noTitle.remove("title");
        expectError(send(HttpMethod.POST, "/api/projects", client, noTitle), 400, "VALIDATION_FAILED");

        Map<String, Object> noBudget = projectBody("50000");
        noBudget.remove("budgetInr");
        expectError(send(HttpMethod.POST, "/api/projects", client, noBudget), 400, "VALIDATION_FAILED");

        Map<String, Object> pastDeadline = projectBody("50000");
        pastDeadline.put("deadline", Instant.now().minus(1, ChronoUnit.DAYS).toString());
        expectError(send(HttpMethod.POST, "/api/projects", client, pastDeadline), 400, "INVALID_DEADLINE");
    }

    @Test
    void closedOrCancelledProjectsDoNotTakeProposals() throws Exception {
        Account client = register("CLIENT");
        Account freelancer = register("FREELANCER");

        long closed = openProject(client, "50000");
        json(send(HttpMethod.POST, "/api/projects/" + closed + "/close", client, null), 200);
        expectError(send(HttpMethod.POST, "/api/projects/" + closed + "/proposals", freelancer, proposalBody("40000", 20)),
                409, "PROJECT_NOT_OPEN");

        long cancelled = openProject(client, "50000");
        json(send(HttpMethod.POST, "/api/projects/" + cancelled + "/cancel", client, null), 200);
        expectError(send(HttpMethod.POST, "/api/projects/" + cancelled + "/proposals", freelancer, proposalBody("40000", 20)),
                409, "PROJECT_NOT_OPEN");
    }

    @Test
    void projectStatusMovesAreValidated() throws Exception {
        Account client = register("CLIENT");
        long id = json(send(HttpMethod.POST, "/api/projects", client, projectBody("50000")), 201).get("id").asLong();

        expectError(send(HttpMethod.POST, "/api/projects/" + id + "/close", client, null), 409, "INVALID_STATUS_TRANSITION"); // DRAFT cannot be closed
        json(send(HttpMethod.POST, "/api/projects/" + id + "/publish", client, null), 200);
        expectError(send(HttpMethod.POST, "/api/projects/" + id + "/publish", client, null), 409, "INVALID_STATUS_TRANSITION");
        json(send(HttpMethod.POST, "/api/projects/" + id + "/close", client, null), 200);
        expectError(send(HttpMethod.POST, "/api/projects/" + id + "/publish", client, null), 409, "INVALID_STATUS_TRANSITION"); // no reopening
        expectError(send(HttpMethod.PUT, "/api/projects/" + id, client, projectBody("50000")), 409, "PROJECT_NOT_EDITABLE");
    }

    @Test
    void closingAProjectRejectsItsOpenProposalsAndClosesTheirOffers() throws Exception {
        Deal d = openDeal();
        json(send(HttpMethod.POST, "/api/projects/" + d.projectId() + "/close", d.client(), null), 200);

        JsonNode proposal = json(send(HttpMethod.GET, "/api/proposals/" + d.proposalId(), d.freelancer(), null), 200);
        assertEquals("REJECTED", proposal.get("status").asText());
        assertEquals("CLOSED", proposal.get("offers").get(0).get("status").asText());
    }

    @Test
    void ownerCanEditButTheBudgetIsLockedOnceProposalsExist() throws Exception {
        Account client = register("CLIENT");
        Account freelancer = register("FREELANCER");
        long id = openProject(client, "50000");

        Map<String, Object> edited = projectBody("70000"); // no proposals yet: budget may change
        edited.put("title", "Renamed project");
        assertEquals("Renamed project", json(send(HttpMethod.PUT, "/api/projects/" + id, client, edited), 200).get("title").asText());

        propose(freelancer, id, "60000", 20);
        expectError(send(HttpMethod.PUT, "/api/projects/" + id, client, projectBody("65000")), 409, "BUDGET_LOCKED");
        Map<String, Object> sameBudget = projectBody("70000");
        sameBudget.put("title", "Renamed again");
        assertEquals("Renamed again", json(send(HttpMethod.PUT, "/api/projects/" + id, client, sameBudget), 200).get("title").asText());
    }

    // =================================================================== B. proposals

    @Test
    void freelancerSubmitsAProposalAndSeesItInTheirList() throws Exception {
        Account client = register("CLIENT");
        Account freelancer = register("FREELANCER");
        long projectId = openProject(client, "60000");

        JsonNode created = json(send(HttpMethod.POST, "/api/projects/" + projectId + "/proposals", freelancer, proposalBody("58000", 30)), 201);
        assertEquals("SUBMITTED", created.get("status").asText());
        assertEquals(freelancer.id(), created.get("freelancer").get("id").asLong());
        assertEquals(1, created.get("offers").size());
        assertEquals("PENDING", created.get("offers").get(0).get("status").asText());
        assertEquals(freelancer.id(), created.get("offers").get(0).get("offeredBy").get("id").asLong());

        assertTrue(containsId(json(send(HttpMethod.GET, "/api/proposals/my", freelancer, null), 200), created.get("id").asLong()));
        expectError(send(HttpMethod.GET, "/api/proposals/my", client, null), 403, "FORBIDDEN");
        expectError(send(HttpMethod.POST, "/api/projects/" + projectId + "/proposals", client, proposalBody("58000", 30)), 403, "FORBIDDEN");
    }

    @Test
    void proposalAboveTheBudgetOrWithBadNumbersIsRejected() throws Exception {
        Account client = register("CLIENT");
        Account freelancer = register("FREELANCER");
        long projectId = openProject(client, "60000");
        String path = "/api/projects/" + projectId + "/proposals";

        expectError(send(HttpMethod.POST, path, freelancer, proposalBody("60000.01", 30)), 400, "PRICE_EXCEEDS_BUDGET");
        expectError(send(HttpMethod.POST, path, freelancer, proposalBody("0", 30)), 400, "VALIDATION_FAILED");
        expectError(send(HttpMethod.POST, path, freelancer, proposalBody("-1", 30)), 400, "VALIDATION_FAILED");
        expectError(send(HttpMethod.POST, path, freelancer, proposalBody("100.005", 30)), 400, "VALIDATION_FAILED");
        expectError(send(HttpMethod.POST, path, freelancer, proposalBody("5000", 0)), 400, "VALIDATION_FAILED");
        assertTrue(proposals.findByProjectIdOrderByCreatedAtDesc(projectId).isEmpty());
    }

    @Test
    void onlyOneActiveProposalPerProjectButResubmissionAfterWithdrawalWorks() throws Exception {
        Account client = register("CLIENT");
        Account freelancer = register("FREELANCER");
        long projectId = openProject(client, "60000");
        String path = "/api/projects/" + projectId + "/proposals";

        long first = propose(freelancer, projectId, "58000", 30);
        expectError(send(HttpMethod.POST, path, freelancer, proposalBody("57000", 30)), 409, "DUPLICATE_PROPOSAL");

        assertEquals("WITHDRAWN", json(send(HttpMethod.POST, "/api/proposals/" + first + "/withdraw", freelancer, null), 200).get("status").asText());
        long second = propose(freelancer, projectId, "56000", 25); // allowed again
        assertTrue(second != first);
        expectError(send(HttpMethod.POST, path, freelancer, proposalBody("55000", 25)), 409, "DUPLICATE_PROPOSAL");
        assertEquals(2, proposals.findByProjectIdAndFreelancerIdOrderByCreatedAtDesc(projectId, freelancer.id()).size());
    }

    @Test
    void resubmissionAfterRejectionWorks() throws Exception {
        Deal d = openDeal();
        json(send(HttpMethod.POST, "/api/proposals/" + d.proposalId() + "/reject", d.client(), null), 200);
        long again = propose(d.freelancer(), d.projectId(), "50000", 20);
        assertTrue(again != d.proposalId());
    }

    @Test
    void clientSeesProposalsOfTheirOwnProjectOnly() throws Exception {
        Deal d = openDeal();
        Account otherClient = register("CLIENT");

        JsonNode list = json(send(HttpMethod.GET, "/api/projects/" + d.projectId() + "/proposals", d.client(), null), 200);
        assertEquals(1, list.size());
        assertEquals(d.proposalId(), list.get(0).get("id").asLong());

        expectError(send(HttpMethod.GET, "/api/projects/" + d.projectId() + "/proposals", otherClient, null), 403, "NOT_PROJECT_OWNER");
        expectError(send(HttpMethod.GET, "/api/projects/" + d.projectId() + "/proposals", d.freelancer(), null), 403, "FORBIDDEN");
    }

    @Test
    void outsidersCannotViewOrChangeSomeoneElsesProposal() throws Exception {
        Deal d = openDeal();
        Account otherFreelancer = register("FREELANCER");
        Account otherClient = register("CLIENT");
        String path = "/api/proposals/" + d.proposalId();

        expectError(send(HttpMethod.GET, path, otherFreelancer, null), 404, "PROPOSAL_NOT_FOUND");
        expectError(send(HttpMethod.GET, path, otherClient, null), 404, "PROPOSAL_NOT_FOUND");
        expectError(send(HttpMethod.POST, path + "/withdraw", otherFreelancer, null), 404, "PROPOSAL_NOT_FOUND");
        expectError(send(HttpMethod.POST, path + "/reject", otherClient, null), 404, "PROPOSAL_NOT_FOUND");
        expectError(send(HttpMethod.POST, path + "/shortlist", otherClient, null), 404, "PROPOSAL_NOT_FOUND");
        expectError(send(HttpMethod.POST, path + "/reject", d.freelancer(), null), 403, "FORBIDDEN"); // a freelancer cannot review proposals
        expectError(send(HttpMethod.GET, path + "/offers", otherFreelancer, null), 404, "PROPOSAL_NOT_FOUND");
        assertEquals(ProposalStatus.SUBMITTED, proposals.findById(d.proposalId()).orElseThrow().getStatus());

        send(HttpMethod.GET, path, d.client(), null).andExpect(status().isOk());
        send(HttpMethod.GET, path, d.freelancer(), null).andExpect(status().isOk());
    }

    @Test
    void invalidProposalStatusMovesAreRejected() throws Exception {
        Deal d = openDeal();
        String path = "/api/proposals/" + d.proposalId();

        assertEquals("SHORTLISTED", json(send(HttpMethod.POST, path + "/shortlist", d.client(), null), 200).get("status").asText());
        expectError(send(HttpMethod.POST, path + "/shortlist", d.client(), null), 409, "INVALID_STATUS_TRANSITION");

        json(send(HttpMethod.POST, path + "/reject", d.client(), null), 200);
        expectError(send(HttpMethod.POST, path + "/reject", d.client(), null), 409, "INVALID_STATUS_TRANSITION");
        expectError(send(HttpMethod.POST, path + "/shortlist", d.client(), null), 409, "INVALID_STATUS_TRANSITION");
        expectError(send(HttpMethod.POST, path + "/withdraw", d.freelancer(), null), 409, "INVALID_STATUS_TRANSITION");
    }

    // =================================================================== C. negotiation

    @Test
    void offersAndCounterOffersAreRecordedInOrder() throws Exception {
        Deal d = openDeal();
        String base = "/api/proposals/" + d.proposalId() + "/offers";

        long first = pendingOfferId(d.client(), d.proposalId());
        JsonNode clientCounter = json(send(HttpMethod.POST, base + "/" + first + "/counter", d.client(), offerBody("52000", 35)), 201);
        assertEquals("PENDING", clientCounter.get("status").asText());
        assertEquals(d.client().id(), clientCounter.get("offeredBy").get("id").asLong());

        JsonNode freelancerCounter = json(send(HttpMethod.POST, base + "/" + clientCounter.get("id").asLong() + "/counter",
                d.freelancer(), offerBody("55000", 32)), 201);
        assertEquals(d.freelancer().id(), freelancerCounter.get("offeredBy").get("id").asLong());

        JsonNode history = json(send(HttpMethod.GET, base, d.client(), null), 200);
        assertEquals(3, history.size());
        assertEquals("COUNTERED", history.get(0).get("status").asText());
        assertEquals("COUNTERED", history.get(1).get("status").asText());
        assertEquals("PENDING", history.get(2).get("status").asText());
        assertEquals(0, new BigDecimal("58000").compareTo(history.get(0).get("priceInr").decimalValue()));
        assertEquals(0, new BigDecimal("52000").compareTo(history.get(1).get("priceInr").decimalValue()));
        assertEquals(0, new BigDecimal("55000").compareTo(history.get(2).get("priceInr").decimalValue()));
        assertEquals(d.client().id(), history.get(0).get("respondedBy").asLong()); // the client answered offer 1
        assertNotNull(history.get(0).get("respondedAt"));
        assertEquals(1, offers.findAll().stream().filter(o -> o.getStatus() == OfferStatus.PENDING
                && o.getProposal().getId().equals(d.proposalId())).count());
    }

    @Test
    void onlyTheOtherPartyCanAcceptAnOffer() throws Exception {
        Deal d = openDeal();
        Account outsider = register("FREELANCER");
        long offerId = pendingOfferId(d.client(), d.proposalId());
        String accept = "/api/proposals/" + d.proposalId() + "/offers/" + offerId + "/accept";

        expectError(send(HttpMethod.POST, accept, d.freelancer(), null), 403, "CANNOT_RESPOND_TO_OWN_OFFER"); // the author
        expectError(send(HttpMethod.POST, accept, outsider, null), 404, "PROPOSAL_NOT_FOUND");
        assertEquals(OfferStatus.PENDING, offers.findById(offerId).orElseThrow().getStatus());

        assertEquals("ACCEPTED", json(send(HttpMethod.POST, accept, d.client(), null), 200).get("status").asText());
        expectError(send(HttpMethod.POST, accept, d.client(), null), 409, "NEGOTIATION_CLOSED");
    }

    @Test
    void cannotStartAnotherOfferWhileOneIsPendingButCanAfterWithdrawingIt() throws Exception {
        Deal d = openDeal();
        String base = "/api/proposals/" + d.proposalId() + "/offers";

        expectError(send(HttpMethod.POST, base, d.freelancer(), offerBody("57000", 30)), 409, "OFFER_PENDING");
        expectError(send(HttpMethod.POST, base, d.client(), offerBody("50000", 30)), 409, "OFFER_PENDING");

        long first = pendingOfferId(d.client(), d.proposalId());
        expectError(send(HttpMethod.POST, base + "/" + first + "/withdraw", d.client(), null), 403, "FORBIDDEN"); // not the author
        assertEquals("WITHDRAWN", json(send(HttpMethod.POST, base + "/" + first + "/withdraw", d.freelancer(), null), 200).get("status").asText());

        JsonNode fresh = json(send(HttpMethod.POST, base, d.client(), offerBody("50000", 30)), 201); // nothing pending any more
        assertEquals("PENDING", fresh.get("status").asText());
        expectError(send(HttpMethod.POST, base + "/" + first + "/accept", d.client(), null), 409, "OFFER_NOT_PENDING");
    }

    @Test
    void alreadyAnsweredOffersCannotBeAnsweredAgain() throws Exception {
        Deal d = openDeal();
        String base = "/api/proposals/" + d.proposalId() + "/offers";
        long first = pendingOfferId(d.client(), d.proposalId());
        json(send(HttpMethod.POST, base + "/" + first + "/counter", d.client(), offerBody("52000", 30)), 201);

        expectError(send(HttpMethod.POST, base + "/" + first + "/counter", d.client(), offerBody("51000", 30)), 409, "OFFER_NOT_PENDING");
        expectError(send(HttpMethod.POST, base + "/" + first + "/accept", d.client(), null), 409, "OFFER_NOT_PENDING");
    }

    @Test
    void offerAmountsAndOwnershipAreValidated() throws Exception {
        Deal d = openDeal();
        String base = "/api/proposals/" + d.proposalId() + "/offers";
        long first = pendingOfferId(d.client(), d.proposalId());
        String counter = base + "/" + first + "/counter";

        expectError(send(HttpMethod.POST, counter, d.client(), offerBody("60000.01", 30)), 400, "PRICE_EXCEEDS_BUDGET");
        expectError(send(HttpMethod.POST, counter, d.client(), offerBody("0", 30)), 400, "VALIDATION_FAILED");
        expectError(send(HttpMethod.POST, counter, d.client(), offerBody("-5", 30)), 400, "VALIDATION_FAILED");
        expectError(send(HttpMethod.POST, counter, d.client(), offerBody("100.999", 30)), 400, "VALIDATION_FAILED");
        expectError(send(HttpMethod.POST, counter, d.client(), offerBody("50000", 0)), 400, "VALIDATION_FAILED");
        assertEquals(OfferStatus.PENDING, offers.findById(first).orElseThrow().getStatus(), "bad counters must not touch the offer");

        // an offer id that belongs to ANOTHER proposal is treated as unknown
        Deal other = openDeal();
        long foreignOffer = pendingOfferId(other.client(), other.proposalId());
        expectError(send(HttpMethod.POST, base + "/" + foreignOffer + "/counter", d.client(), offerBody("50000", 30)), 404, "OFFER_NOT_FOUND");
    }

    @Test
    void noNegotiationOnProposalsThatAreRejectedOrWithdrawn() throws Exception {
        Deal rejected = openDeal();
        long offerId = pendingOfferId(rejected.client(), rejected.proposalId());
        json(send(HttpMethod.POST, "/api/proposals/" + rejected.proposalId() + "/reject", rejected.client(), null), 200);
        assertEquals(OfferStatus.CLOSED, offers.findById(offerId).orElseThrow().getStatus());
        expectError(send(HttpMethod.POST, "/api/proposals/" + rejected.proposalId() + "/offers/" + offerId + "/counter",
                rejected.client(), offerBody("50000", 30)), 409, "PROPOSAL_NOT_ACTIVE");

        Deal withdrawn = openDeal();
        json(send(HttpMethod.POST, "/api/proposals/" + withdrawn.proposalId() + "/withdraw", withdrawn.freelancer(), null), 200);
        expectError(send(HttpMethod.POST, "/api/proposals/" + withdrawn.proposalId() + "/offers", withdrawn.client(), offerBody("50000", 30)),
                409, "PROPOSAL_NOT_ACTIVE");
    }

    // =================================================================== D. agreements

    @Test
    void agreementCannotBeCreatedWhileAnOfferIsPending() throws Exception {
        Deal d = openDeal();
        expectError(send(HttpMethod.POST, "/api/proposals/" + d.proposalId() + "/accept", d.client(), null), 409, "OFFER_PENDING");

        // a pending COUNTER-offer blocks it too
        long first = pendingOfferId(d.client(), d.proposalId());
        json(send(HttpMethod.POST, "/api/proposals/" + d.proposalId() + "/offers/" + first + "/counter", d.client(), offerBody("52000", 30)), 201);
        expectError(send(HttpMethod.POST, "/api/proposals/" + d.proposalId() + "/accept", d.client(), null), 409, "OFFER_PENDING");
    }

    @Test
    void agreementCannotBeCreatedBeforeTheTermsAreAgreed() throws Exception {
        Deal d = openDeal();
        long first = pendingOfferId(d.client(), d.proposalId());
        json(send(HttpMethod.POST, "/api/proposals/" + d.proposalId() + "/offers/" + first + "/withdraw", d.freelancer(), null), 200);
        // nothing pending and nothing accepted
        expectError(send(HttpMethod.POST, "/api/proposals/" + d.proposalId() + "/accept", d.client(), null), 409, "TERMS_NOT_AGREED");
    }

    @Test
    void failedAgreementCreationLeavesEverythingUnchanged() throws Exception {
        Deal d = openDeal();
        Account second = register("FREELANCER");
        long otherProposal = propose(second, d.projectId(), "50000", 20);

        expectError(send(HttpMethod.POST, "/api/proposals/" + d.proposalId() + "/accept", d.client(), null), 409, "OFFER_PENDING");

        assertTrue(agreements.findByProposalId(d.proposalId()).isEmpty());
        assertEquals(ProposalStatus.SUBMITTED, proposals.findById(d.proposalId()).orElseThrow().getStatus());
        assertEquals(ProposalStatus.SUBMITTED, proposals.findById(otherProposal).orElseThrow().getStatus());
        assertEquals(ProjectStatus.OPEN, projects.findById(d.projectId()).orElseThrow().getStatus());
    }

    @Test
    void rejectedOrWithdrawnProposalsCannotBecomeAgreements() throws Exception {
        Deal rejected = openDeal();
        clientAcceptsFirstOffer(rejected);
        json(send(HttpMethod.POST, "/api/proposals/" + rejected.proposalId() + "/reject", rejected.client(), null), 200);
        expectError(send(HttpMethod.POST, "/api/proposals/" + rejected.proposalId() + "/accept", rejected.client(), null),
                409, "INVALID_STATUS_TRANSITION");

        Deal withdrawn = openDeal();
        clientAcceptsFirstOffer(withdrawn);
        json(send(HttpMethod.POST, "/api/proposals/" + withdrawn.proposalId() + "/withdraw", withdrawn.freelancer(), null), 200);
        expectError(send(HttpMethod.POST, "/api/proposals/" + withdrawn.proposalId() + "/accept", withdrawn.client(), null),
                409, "INVALID_STATUS_TRANSITION");
        assertTrue(agreements.findByProposalId(rejected.proposalId()).isEmpty());
        assertTrue(agreements.findByProposalId(withdrawn.proposalId()).isEmpty());
    }

    @Test
    void onlyTheProjectsOwnClientCanCreateTheAgreement() throws Exception {
        Deal d = openDeal();
        clientAcceptsFirstOffer(d);
        Account otherClient = register("CLIENT");
        String path = "/api/proposals/" + d.proposalId() + "/accept";

        expectError(send(HttpMethod.POST, path, d.freelancer(), null), 403, "FORBIDDEN");
        expectError(send(HttpMethod.POST, path, otherClient, null), 404, "PROPOSAL_NOT_FOUND");
        assertTrue(agreements.findByProposalId(d.proposalId()).isEmpty());
    }

    @Test
    void agreementIsCreatedAfterNegotiationWithTheCorrectPartiesAndPrice() throws Exception {
        Deal d = openDeal();
        Account rival = register("FREELANCER");
        long rivalProposal = propose(rival, d.projectId(), "50000", 20);
        String base = "/api/proposals/" + d.proposalId() + "/offers";

        // negotiation: freelancer asked 58000, the client counters 55000, the freelancer accepts
        long first = pendingOfferId(d.client(), d.proposalId());
        JsonNode counter = json(send(HttpMethod.POST, base + "/" + first + "/counter", d.client(), offerBody("55000", 28)), 201);
        json(send(HttpMethod.POST, base + "/" + counter.get("id").asLong() + "/accept", d.freelancer(), null), 200);

        JsonNode agreement = json(send(HttpMethod.POST, "/api/proposals/" + d.proposalId() + "/accept", d.client(), null), 201);

        assertEquals("FINALIZED", agreement.get("status").asText());
        assertEquals(d.proposalId(), agreement.get("proposalId").asLong());
        assertEquals(d.projectId(), agreement.get("projectId").asLong());
        assertEquals(d.client().id(), agreement.get("client").get("id").asLong());
        assertEquals(d.freelancer().id(), agreement.get("freelancer").get("id").asLong());
        assertEquals(0, new BigDecimal("55000").compareTo(agreement.get("priceInr").decimalValue()));
        assertEquals("220000000000000000", agreement.get("totalWei").asText()); // 55000 / 250000 = 0.22 ETH
        assertTrue(agreement.get("snapshotHash").asText().matches("0x[0-9a-f]{64}"));
        assertTrue(agreement.get("snapshotIntegrityOk").asBoolean());
        assertNotNull(agreement.get("clientFinalizedAt"));
        assertNotNull(agreement.get("freelancerFinalizedAt"));

        JsonNode snapshot = agreement.get("snapshot");
        assertEquals("55000.00", snapshot.get("agreedTerms").get("priceInr").asText());
        assertEquals(28, snapshot.get("agreedTerms").get("durationDays").asInt());
        assertEquals("Build an online store", snapshot.get("project").get("title").asText());
        assertEquals("60000.00", snapshot.get("project").get("budgetInr").asText());
        assertEquals("250000", snapshot.get("pricing").get("inrPerEth").asText());
        assertEquals(2, snapshot.get("negotiationHistory").size());
        assertEquals("COUNTERED", snapshot.get("negotiationHistory").get(0).get("status").asText());
        assertEquals("ACCEPTED", snapshot.get("negotiationHistory").get(1).get("status").asText());

        // everything moved together
        assertEquals(ProposalStatus.ACCEPTED, proposals.findById(d.proposalId()).orElseThrow().getStatus());
        assertEquals(ProjectStatus.IN_PROGRESS, projects.findById(d.projectId()).orElseThrow().getStatus());
        assertEquals(ProposalStatus.REJECTED, proposals.findById(rivalProposal).orElseThrow().getStatus());

        // the snapshot survives a real round trip through MySQL JSON with an intact hash
        em.flush();
        em.clear();
        JsonNode reread = json(send(HttpMethod.GET, "/api/agreements/" + agreement.get("id").asLong(), d.freelancer(), null), 200);
        assertTrue(reread.get("snapshotIntegrityOk").asBoolean(), "the stored snapshot must still match its hash");
        assertEquals(agreement.get("snapshotHash").asText(), reread.get("snapshotHash").asText());
    }

    @Test
    void acceptingTheFreelancersFirstOfferAlsoWorks() throws Exception {
        Deal d = openDeal();
        clientAcceptsFirstOffer(d);
        JsonNode agreement = json(send(HttpMethod.POST, "/api/proposals/" + d.proposalId() + "/accept", d.client(), null), 201);
        assertEquals(0, new BigDecimal("58000").compareTo(agreement.get("priceInr").decimalValue()));
        assertEquals(1, agreement.get("snapshot").get("negotiationHistory").size());
    }

    @Test
    void editingTheProjectLaterDoesNotChangeTheSnapshot() throws Exception {
        Deal d = openDeal();
        clientAcceptsFirstOffer(d);
        JsonNode created = json(send(HttpMethod.POST, "/api/proposals/" + d.proposalId() + "/accept", d.client(), null), 201);
        long agreementId = created.get("id").asLong();
        String originalHash = created.get("snapshotHash").asText();

        // 1. through the API, editing is refused because the project is now IN_PROGRESS
        Map<String, Object> edit = projectBody("60000");
        edit.put("title", "CHANGED TITLE");
        expectError(send(HttpMethod.PUT, "/api/projects/" + d.projectId(), d.client(), edit), 409, "PROJECT_NOT_EDITABLE");

        // 2. even a direct database change to the live project does not touch the stored snapshot
        Project project = projects.findById(d.projectId()).orElseThrow();
        project.setTitle("CHANGED DIRECTLY IN THE DATABASE");
        projects.saveAndFlush(project);
        Proposal proposal = proposals.findById(d.proposalId()).orElseThrow();
        proposal.setMessage("A different message");
        proposals.saveAndFlush(proposal);
        em.flush();
        em.clear();

        JsonNode after = json(send(HttpMethod.GET, "/api/agreements/" + agreementId, d.client(), null), 200);
        assertEquals("Build an online store", after.get("snapshot").get("project").get("title").asText());
        assertEquals("I have built several stores like this.", after.get("snapshot").get("proposal").get("message").asText());
        assertEquals(originalHash, after.get("snapshotHash").asText());
        assertTrue(after.get("snapshotIntegrityOk").asBoolean());
    }

    @Test
    void agreementsHaveNoEditEndpointAndOnlyParticipantsCanReadThem() throws Exception {
        Deal d = openDeal();
        clientAcceptsFirstOffer(d);
        long id = json(send(HttpMethod.POST, "/api/proposals/" + d.proposalId() + "/accept", d.client(), null), 201).get("id").asLong();
        Account outsider = register("CLIENT");
        Account outsiderFreelancer = register("FREELANCER");

        send(HttpMethod.GET, "/api/agreements/" + id, d.client(), null).andExpect(status().isOk());
        send(HttpMethod.GET, "/api/agreements/" + id, d.freelancer(), null).andExpect(status().isOk());
        expectError(send(HttpMethod.GET, "/api/agreements/" + id, outsider, null), 404, "AGREEMENT_NOT_FOUND");
        expectError(send(HttpMethod.GET, "/api/agreements/" + id, outsiderFreelancer, null), 404, "AGREEMENT_NOT_FOUND");

        assertTrue(containsId(json(send(HttpMethod.GET, "/api/agreements/my", d.client(), null), 200), id));
        assertTrue(containsId(json(send(HttpMethod.GET, "/api/agreements/my", d.freelancer(), null), 200), id));
        assertEquals(0, json(send(HttpMethod.GET, "/api/agreements/my", outsider, null), 200).size());

        Map<String, Object> tamper = Map.of("totalWei", "1");
        send(HttpMethod.PUT, "/api/agreements/" + id, d.client(), tamper).andExpect(status().isMethodNotAllowed());
        send(HttpMethod.DELETE, "/api/agreements/" + id, d.client(), null).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void duplicateAgreementCreationIsPrevented() throws Exception {
        Deal d = openDeal();
        clientAcceptsFirstOffer(d);
        String path = "/api/proposals/" + d.proposalId() + "/accept";
        json(send(HttpMethod.POST, path, d.client(), null), 201);

        expectError(send(HttpMethod.POST, path, d.client(), null), 409, "AGREEMENT_EXISTS"); // a second click
        assertEquals(1, agreements.findAll().stream().filter(a -> a.getProposal().getId().equals(d.proposalId())).count());

        // the database itself refuses a second agreement for the same proposal (UNIQUE uq_agreement_proposal)
        Agreement duplicate = new Agreement();
        duplicate.setProposal(proposals.findById(d.proposalId()).orElseThrow());
        duplicate.setTotalWei(BigDecimal.ONE);
        duplicate.setStatus(AgreementStatus.DRAFT);
        DataIntegrityViolationException e = assertThrows(DataIntegrityViolationException.class, () -> agreements.saveAndFlush(duplicate));
        assertTrue(String.valueOf(e.getMostSpecificCause().getMessage()).contains("uq_agreement_proposal"),
                "expected the uq_agreement_proposal constraint but got: " + e.getMostSpecificCause().getMessage());
    }
}
