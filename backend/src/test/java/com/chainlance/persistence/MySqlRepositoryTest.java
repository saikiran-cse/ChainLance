package com.chainlance.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chainlance.entity.Agreement;
import com.chainlance.entity.BlockchainTransaction;
import com.chainlance.entity.DeadlineExtensionRequest;
import com.chainlance.entity.Dispute;
import com.chainlance.entity.DisputeResponse;
import com.chainlance.entity.Milestone;
import com.chainlance.entity.NegotiationMessage;
import com.chainlance.entity.Project;
import com.chainlance.entity.ProjectMilestonePlan;
import com.chainlance.entity.Proposal;
import com.chainlance.entity.Review;
import com.chainlance.entity.SettlementProposal;
import com.chainlance.entity.Submission;
import com.chainlance.entity.User;
import com.chainlance.entity.WalletAddress;
import com.chainlance.entity.WalletChallenge;
import com.chainlance.entity.enums.DisputeStatus;
import com.chainlance.entity.enums.ExtensionStatus;
import com.chainlance.entity.enums.Role;
import com.chainlance.entity.enums.SettlementStatus;
import com.chainlance.entity.enums.SubmissionReviewStatus;
import com.chainlance.entity.enums.TransactionStatus;
import com.chainlance.entity.enums.TransactionType;
import com.chainlance.entity.enums.AgreementStatus;
import com.chainlance.entity.enums.DisputeDecision;
import com.chainlance.repository.AgreementRepository;
import com.chainlance.repository.BlockchainTransactionRepository;
import com.chainlance.repository.DeadlineExtensionRequestRepository;
import com.chainlance.repository.DisputeRepository;
import com.chainlance.repository.DisputeResponseRepository;
import com.chainlance.repository.MilestoneRepository;
import com.chainlance.repository.NegotiationMessageRepository;
import com.chainlance.repository.ProjectMilestonePlanRepository;
import com.chainlance.repository.ProjectRepository;
import com.chainlance.repository.ProposalRepository;
import com.chainlance.repository.ReviewRepository;
import com.chainlance.repository.SettlementProposalRepository;
import com.chainlance.repository.SubmissionRepository;
import com.chainlance.repository.UserRepository;
import com.chainlance.repository.WalletAddressRepository;
import com.chainlance.repository.WalletChallengeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * Opt-in test against a REAL MySQL database. It is skipped unless TEST_DB_URL is set.
 * It starts Spring with Flyway and Hibernate schema validation (ddl-auto=validate), then exercises every table.
 * Every test runs in a transaction that is rolled back. For safety the URL must contain "_test"
 * (use a separate database such as chainlance_test, never your normal one).
 *
 * Variables: TEST_DB_URL, TEST_DB_USERNAME (default chainlance), TEST_DB_PASSWORD.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.clean-disabled=true"
})
@EnabledIfEnvironmentVariable(named = "TEST_DB_URL", matches = ".+")
class MySqlRepositoryTest {

    private static final BigDecimal TEN_POW_30 = new BigDecimal("1000000000000000000000000000000");
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

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

    @PersistenceContext
    private EntityManager em;

    @Autowired private UserRepository users;
    @Autowired private WalletAddressRepository wallets;
    @Autowired private WalletChallengeRepository challenges;
    @Autowired private ProjectRepository projects;
    @Autowired private ProjectMilestonePlanRepository plans;
    @Autowired private ProposalRepository proposals;
    @Autowired private NegotiationMessageRepository negotiations;
    @Autowired private AgreementRepository agreements;
    @Autowired private MilestoneRepository milestones;
    @Autowired private SubmissionRepository submissions;
    @Autowired private BlockchainTransactionRepository transactions;
    @Autowired private DeadlineExtensionRequestRepository extensions;
    @Autowired private SettlementProposalRepository settlements;
    @Autowired private DisputeRepository disputes;
    @Autowired private DisputeResponseRepository disputeResponses;
    @Autowired private ReviewRepository reviews;

    // ------------------------------------------------------------------ helpers

    private static String hex(int n) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < n) {
            sb.append(UUID.randomUUID().toString().replace("-", ""));
        }
        return sb.substring(0, n);
    }

    private User newUser(String prefix, Role role) {
        User u = new User();
        u.setEmail(prefix + "-" + hex(12) + "@example.test");
        u.setPasswordHash("not-a-real-hash");
        u.setDisplayName(prefix);
        u.setRole(role);
        return users.save(u);
    }

    private Milestone newMilestone(User client, User freelancer) {
        Project p = new Project();
        p.setClient(client);
        p.setTitle("t");
        p.setDescription("d");
        p.setCategory("web");
        p.setBudgetInr(new BigDecimal("1000.00"));
        projects.save(p);

        Proposal pr = new Proposal();
        pr.setProject(p);
        pr.setFreelancer(freelancer);
        pr.setMessage("m");
        pr.setPriceInr(new BigDecimal("900.00"));
        pr.setDurationDays(10);
        proposals.save(pr);

        Agreement a = new Agreement();
        a.setProposal(pr);
        a.setTotalWei(TEN_POW_30);
        agreements.save(a);

        Milestone m = new Milestone();
        m.setAgreement(a);
        m.setPosition(1);
        m.setDescription("first");
        m.setAmountWei(TEN_POW_30);
        milestones.save(m);
        em.flush();
        return m;
    }

    // ------------------------------------------------------------------ tests

    @Test
    void flywayMigrationWasAppliedAndHibernateValidationPassed() {
        // Reaching this line means the Spring context started with ddl-auto=validate.
        Number applied = (Number) em.createNativeQuery("select count(*) from flyway_schema_history where success = 1")
                .getSingleResult();
        assertTrue(applied.longValue() >= 1, "Flyway should have applied V1");
    }

    @Test
    void allSixteenTablesRoundTripWithExactValues() {
        User client = newUser("client", Role.CLIENT);
        User freelancer = newUser("freelancer", Role.FREELANCER);
        newUser("admin", Role.ADMIN);
        String clientWallet = "0x" + hex(40);
        String freelancerWallet = "0x" + hex(40);

        WalletAddress wa = new WalletAddress();
        wa.setUser(client);
        wa.setAddress(clientWallet);
        wa.setVerifiedAt(T0);
        wallets.save(wa);

        WalletChallenge ch = new WalletChallenge();
        ch.setUser(client);
        ch.setAddress(clientWallet);
        ch.setNonce(hex(32));
        ch.setMessage("Sign in to ChainLance");
        ch.setExpiresAt(T0.plusSeconds(600));
        challenges.save(ch);

        Project project = new Project();
        project.setClient(client);
        project.setTitle("Build a website");
        project.setDescription("Long description");
        project.setCategory("web");
        project.setSkills("java,react");
        project.setBudgetInr(new BigDecimal("50000.00"));
        project.setDeadline(T0.plusSeconds(30L * 86400));
        projects.save(project);

        ProjectMilestonePlan plan = new ProjectMilestonePlan();
        plan.setProject(project);
        plan.setPosition(1);
        plan.setDescription("Design");
        plan.setAllocationInr(new BigDecimal("25000.00"));
        plans.save(plan);

        Proposal proposal = new Proposal();
        proposal.setProject(project);
        proposal.setFreelancer(freelancer);
        proposal.setMessage("I can do it");
        proposal.setPriceInr(new BigDecimal("45000.00"));
        proposal.setDurationDays(30);
        proposals.save(proposal);

        NegotiationMessage nm = new NegotiationMessage();
        nm.setProposal(proposal);
        nm.setSender(freelancer);
        nm.setMessage("Counter offer");
        nm.setProposedTerms(Map.of("priceInr", "44000.00",
                "milestones", List.of(Map.of("position", 1, "amountWei", "1000000000000000000"))));
        negotiations.save(nm);

        Agreement agreement = new Agreement();
        agreement.setProposal(proposal);
        agreement.setTotalWei(TEN_POW_30);
        agreement.setSnapshot(Map.of("proposalId", 1, "note", "snapshot"));
        agreement.setSnapshotHash("0x" + hex(64));
        agreement.setClientWallet(clientWallet);
        agreement.setFreelancerWallet(freelancerWallet);
        agreement.setOnchainProjectId("0x" + hex(64));
        agreement.setFactoryAddress("0x" + hex(40));
        String escrow = "0x" + hex(40);
        agreement.setEscrowAddress(escrow);
        agreement.setChainId(31337L);
        agreements.save(agreement);

        Milestone milestone = new Milestone();
        milestone.setAgreement(agreement);
        milestone.setPosition(1);
        milestone.setDescription("Design phase");
        milestone.setAcceptanceCriteria("Figma file");
        milestone.setAmountWei(TEN_POW_30);
        milestone.setOriginalDeadline(T0.plusSeconds(10L * 86400));
        milestone.setEffectiveDeadline(T0.plusSeconds(10L * 86400));
        milestones.save(milestone);

        Submission submission = new Submission();
        submission.setMilestone(milestone);
        submission.setDescription("Done");
        submission.setLinks("https://example.test/a");
        submission.setLate(true);
        submissions.save(submission);

        BlockchainTransaction tx = new BlockchainTransaction();
        tx.setTxHash("0x" + hex(64));
        tx.setType(TransactionType.CREATE_ESCROW);
        tx.setStatus(TransactionStatus.CONFIRMED);
        tx.setAgreement(agreement);
        tx.setSubmittedBy(client);
        tx.setFromAddress(clientWallet);
        tx.setToAddress(agreement.getFactoryAddress());
        tx.setChainId(31337L);
        tx.setBlockNumber(123L);
        tx.setValueWei(new BigDecimal("1000000000000000000"));
        tx.setEvent(Map.of("event", "EscrowCreated"));
        tx.setVerifiedAt(T0);
        transactions.save(tx);

        DeadlineExtensionRequest ext = new DeadlineExtensionRequest();
        ext.setMilestone(milestone);
        ext.setRequestedBy(freelancer);
        ext.setCurrentDeadline(T0.plusSeconds(10L * 86400));
        ext.setRequestedDeadline(T0.plusSeconds(20L * 86400));
        ext.setReason("Need more time");
        extensions.save(ext);

        SettlementProposal sp = new SettlementProposal();
        sp.setMilestone(milestone);
        sp.setProposer(client);
        sp.setFreelancerWei(new BigDecimal("600000000000000000000000000000"));
        sp.setClientWei(new BigDecimal("400000000000000000000000000000"));
        sp.setStatus(SettlementStatus.ACTIVE);
        sp.setProposeTx(tx);
        settlements.save(sp);

        Dispute dispute = new Dispute();
        dispute.setMilestone(milestone);
        dispute.setOpenedBy(freelancer);
        dispute.setReason("Not paid");
        dispute.setReasonHash("0x" + hex(64));
        dispute.setStatus(DisputeStatus.OPEN);
        dispute.setOpenTx(tx);
        disputes.save(dispute);

        DisputeResponse dr = new DisputeResponse();
        dr.setDispute(dispute);
        dr.setUser(client);
        dr.setResponse("It was late");
        disputeResponses.save(dr);

        Review review = new Review();
        review.setAgreement(agreement);
        review.setReviewer(client);
        review.setReviewee(freelancer);
        review.setRating((byte) 5);
        review.setComment("Great");
        reviews.save(review);

        em.flush();
        em.clear();

        // ---- read everything back from MySQL and compare exact values
        User loadedClient = users.findByEmail(client.getEmail()).orElseThrow();
        assertEquals(Role.CLIENT, loadedClient.getRole());
        assertTrue(loadedClient.isEnabled());
        assertNotNull(loadedClient.getCreatedAt());
        assertTrue(users.existsByEmail(client.getEmail()));

        assertEquals(clientWallet, wallets.findByAddress(clientWallet).orElseThrow().getAddress());
        assertEquals(1, wallets.findByUserIdOrderByCreatedAtAsc(client.getId()).size());

        Project loadedProject = projects.findById(project.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("50000.00").compareTo(loadedProject.getBudgetInr()));
        assertEquals(T0.plusSeconds(30L * 86400), loadedProject.getDeadline());
        assertEquals(1, projects.findByClientIdOrderByCreatedAtDesc(client.getId()).size());
        assertEquals(1, plans.findByProjectIdOrderByPositionAsc(project.getId()).size());

        assertTrue(proposals.existsByProjectIdAndFreelancerId(project.getId(), freelancer.getId()));
        assertEquals(1, proposals.findByProjectIdOrderByCreatedAtDesc(project.getId()).size());
        assertEquals(1, proposals.findByFreelancerIdOrderByCreatedAtDesc(freelancer.getId()).size());

        NegotiationMessage loadedNm = negotiations.findByProposalIdOrderByCreatedAtAsc(proposal.getId()).get(0);
        assertEquals("44000.00", loadedNm.getProposedTerms().get("priceInr"));
        assertTrue(loadedNm.getProposedTerms().get("milestones") instanceof List);

        Agreement loadedAgreement = agreements.findByEscrowAddress(escrow).orElseThrow();
        assertEquals(0, TEN_POW_30.compareTo(loadedAgreement.getTotalWei()), "wei must round-trip exactly");
        assertEquals(AgreementStatus.DRAFT, loadedAgreement.getStatus());
        assertEquals("snapshot", loadedAgreement.getSnapshot().get("note"));
        assertEquals(31337L, loadedAgreement.getChainId().longValue());
        assertEquals(agreement.getOnchainProjectId(),
                agreements.findByOnchainProjectId(agreement.getOnchainProjectId()).orElseThrow().getOnchainProjectId());
        assertEquals(1, agreements.findAllForParticipant(client.getId()).size());
        assertEquals(1, agreements.findAllForParticipant(freelancer.getId()).size());

        Milestone loadedMilestone = milestones.findByAgreementIdAndPosition(agreement.getId(), 1).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(loadedMilestone.getFreelancerPaidWei()));
        assertEquals(1, milestones.findByAgreementIdOrderByPositionAsc(agreement.getId()).size());

        Submission loadedSubmission = submissions.findFirstByMilestoneIdOrderBySubmittedAtDesc(milestone.getId()).orElseThrow();
        assertTrue(loadedSubmission.isLate());
        assertEquals(SubmissionReviewStatus.PENDING, loadedSubmission.getReviewStatus());

        BlockchainTransaction loadedTx = transactions.findByTxHash(tx.getTxHash()).orElseThrow();
        assertEquals(TransactionType.CREATE_ESCROW, loadedTx.getType());
        assertEquals("EscrowCreated", loadedTx.getEvent().get("event"));
        assertEquals(0, new BigDecimal("1000000000000000000").compareTo(loadedTx.getValueWei()));
        assertTrue(transactions.existsByTxHash(tx.getTxHash()));
        assertEquals(1, transactions.findBySubmittedByIdOrderByCreatedAtDesc(client.getId()).size());

        assertTrue(extensions.findByMilestoneIdAndStatus(milestone.getId(), ExtensionStatus.PENDING).isPresent());
        assertEquals(1, extensions.findByMilestoneIdOrderByRequestedAtDesc(milestone.getId()).size());

        assertTrue(settlements.findByMilestoneIdAndStatusIn(milestone.getId(),
                List.of(SettlementStatus.AWAITING_PROPOSE_TX, SettlementStatus.ACTIVE)).isPresent());

        assertTrue(disputes.findByMilestoneIdAndStatusIn(milestone.getId(),
                List.of(DisputeStatus.AWAITING_OPEN_TX, DisputeStatus.OPEN, DisputeStatus.DECIDED)).isPresent());
        assertEquals(1, disputes.findByStatusIn(List.of(DisputeStatus.OPEN), PageRequest.of(0, 10)).getContent().size());
        assertEquals(1, disputes.findAllForParticipant(client.getId()).size());
        assertEquals(1, disputeResponses.findByDisputeIdOrderByCreatedAtAsc(dispute.getId()).size());

        assertTrue(reviews.existsByAgreementIdAndReviewerId(agreement.getId(), client.getId()));
        assertEquals(1, reviews.findByRevieweeIdOrderByCreatedAtDesc(freelancer.getId()).size());
        assertEquals(1L, reviews.countByRevieweeId(freelancer.getId()));
        assertEquals(5.0, reviews.findAverageRatingForReviewee(freelancer.getId()).doubleValue());
    }

    @Test
    void instantsAreStoredAsUtcWallClockTime() {
        User client = newUser("client", Role.CLIENT);
        Project p = new Project();
        p.setClient(client);
        p.setTitle("t");
        p.setDescription("d");
        p.setCategory("web");
        p.setBudgetInr(new BigDecimal("1.00"));
        p.setDeadline(Instant.parse("2026-01-31T00:00:00Z"));
        projects.save(p);
        em.flush();

        Object raw = em.createNativeQuery("select date_format(deadline, '%Y-%m-%d %H:%i:%s') from projects where id = ?1")
                .setParameter(1, p.getId()).getSingleResult();
        assertEquals("2026-01-31 00:00:00", raw.toString());
    }

    @Test
    void agreementVersionIncrementsOnUpdate() {
        Milestone m = newMilestone(newUser("client", Role.CLIENT), newUser("freelancer", Role.FREELANCER));
        Agreement a = m.getAgreement();
        assertEquals(0L, a.getVersion());
        a.setStatus(AgreementStatus.FINALIZED);
        agreements.saveAndFlush(a);
        assertEquals(1L, a.getVersion());
    }

    @Test
    void walletChallengeCanBeConsumedOnlyOnceAndNotAfterExpiry() {
        User u = newUser("client", Role.CLIENT);
        WalletChallenge ch = new WalletChallenge();
        ch.setUser(u);
        ch.setAddress("0x" + hex(40));
        ch.setNonce(hex(32));
        ch.setMessage("msg");
        ch.setExpiresAt(T0.plusSeconds(600));
        challenges.saveAndFlush(ch);
        Long id = ch.getId();

        assertEquals(0, challenges.markUsedIfValid(id, T0.plusSeconds(601)), "expired challenge must not be consumed");
        assertEquals(1, challenges.markUsedIfValid(id, T0.plusSeconds(60)));
        assertEquals(0, challenges.markUsedIfValid(id, T0.plusSeconds(61)), "second use must fail");
        assertEquals(T0.plusSeconds(60), challenges.findById(id).orElseThrow().getUsedAt());
    }

    @Test
    void databaseAllowsOnlyOneLiveSettlementPerMilestone() {
        Milestone m = newMilestone(newUser("client", Role.CLIENT), newUser("freelancer", Role.FREELANCER));
        User proposer = m.getAgreement().getProposal().getProject().getClient();

        settlements.saveAndFlush(settlement(m, proposer, SettlementStatus.ACTIVE));
        settlements.saveAndFlush(settlement(m, proposer, SettlementStatus.WITHDRAWN)); // not live: allowed
        assertThrows(DataIntegrityViolationException.class,
                () -> settlements.saveAndFlush(settlement(m, proposer, SettlementStatus.AWAITING_PROPOSE_TX)));
    }

    @Test
    void databaseAllowsOnlyOnePendingExtensionPerMilestone() {
        User client = newUser("client", Role.CLIENT);
        User freelancer = newUser("freelancer", Role.FREELANCER);
        Milestone m = newMilestone(client, freelancer);

        extensions.saveAndFlush(extension(m, freelancer, ExtensionStatus.REJECTED)); // not pending: allowed
        extensions.saveAndFlush(extension(m, freelancer, ExtensionStatus.PENDING));
        assertThrows(DataIntegrityViolationException.class,
                () -> extensions.saveAndFlush(extension(m, freelancer, ExtensionStatus.PENDING)));
    }

    @Test
    void databaseAllowsOnlyOneLiveDisputePerMilestone() {
        User client = newUser("client", Role.CLIENT);
        User freelancer = newUser("freelancer", Role.FREELANCER);
        Milestone m = newMilestone(client, freelancer);

        disputes.saveAndFlush(dispute(m, client, DisputeStatus.RESOLVED)); // not live: allowed
        disputes.saveAndFlush(dispute(m, client, DisputeStatus.OPEN));
        assertThrows(DataIntegrityViolationException.class,
                () -> disputes.saveAndFlush(dispute(m, freelancer, DisputeStatus.AWAITING_OPEN_TX)));
    }

    @Test
    void databaseRejectsDuplicateTransactionHash() {
        String hash = "0x" + hex(64);
        transactions.saveAndFlush(tx(hash));
        assertThrows(DataIntegrityViolationException.class, () -> transactions.saveAndFlush(tx(hash)));
    }

    @Test
    void adminDecisionFieldsRoundTrip() {
        User client = newUser("client", Role.CLIENT);
        User freelancer = newUser("freelancer", Role.FREELANCER);
        User admin = newUser("admin", Role.ADMIN);
        Milestone m = newMilestone(client, freelancer);
        Dispute d = dispute(m, client, DisputeStatus.DECIDED);
        d.setAdminDecision(DisputeDecision.SPLIT);
        d.setAdminFreelancerWei(new BigDecimal("600000000000000000000000000000"));
        d.setAdminClientWei(new BigDecimal("400000000000000000000000000000"));
        d.setAdminExplanation("Split");
        d.setResolutionHash("0x" + hex(64));
        d.setDecidedBy(admin);
        d.setDecidedAt(T0);
        disputes.saveAndFlush(d);
        em.clear();

        Dispute loaded = disputes.findById(d.getId()).orElseThrow();
        assertEquals(DisputeDecision.SPLIT, loaded.getAdminDecision());
        assertEquals(0, new BigDecimal("600000000000000000000000000000").compareTo(loaded.getAdminFreelancerWei()));
        assertFalse(loaded.getResolutionHash() == null);
    }

    // ------------------------------------------------------------------ builders

    private SettlementProposal settlement(Milestone m, User proposer, SettlementStatus status) {
        SettlementProposal s = new SettlementProposal();
        s.setMilestone(m);
        s.setProposer(proposer);
        s.setFreelancerWei(new BigDecimal("1"));
        s.setClientWei(new BigDecimal("1"));
        s.setStatus(status);
        return s;
    }

    private DeadlineExtensionRequest extension(Milestone m, User by, ExtensionStatus status) {
        DeadlineExtensionRequest e = new DeadlineExtensionRequest();
        e.setMilestone(m);
        e.setRequestedBy(by);
        e.setCurrentDeadline(T0);
        e.setRequestedDeadline(T0.plusSeconds(86400));
        e.setReason("r");
        e.setStatus(status);
        return e;
    }

    private Dispute dispute(Milestone m, User by, DisputeStatus status) {
        Dispute d = new Dispute();
        d.setMilestone(m);
        d.setOpenedBy(by);
        d.setReason("r");
        d.setStatus(status);
        return d;
    }

    private BlockchainTransaction tx(String hash) {
        BlockchainTransaction t = new BlockchainTransaction();
        t.setTxHash(hash);
        t.setType(TransactionType.FUND);
        t.setChainId(31337L);
        return t;
    }
}
