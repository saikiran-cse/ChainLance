package com.chainlance.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Runs anywhere: no database, no credentials. Reads V1__init.sql from the classpath. */
class EntityMappingMatchesMigrationTest {

    static final List<Class<?>> ENTITIES = List.of(
            User.class, WalletAddress.class, WalletChallenge.class, Project.class, ProjectMilestonePlan.class,
            Proposal.class, NegotiationMessage.class, Agreement.class, Milestone.class, Submission.class,
            BlockchainTransaction.class, DeadlineExtensionRequest.class, SettlementProposal.class, Dispute.class,
            DisputeResponse.class, Review.class);

    static String readMigration() throws IOException {
        try (InputStream in = EntityMappingMatchesMigrationTest.class.getClassLoader()
                .getResourceAsStream("db/migration/V1__init.sql")) {
            assertTrue(in != null, "db/migration/V1__init.sql not found on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void migrationDefinesSixteenTables() throws IOException {
        assertEquals(16, MigrationSchemaChecker.parse(readMigration()).size());
    }

    @Test
    void everyTableHasAnEntityAndEveryMappingMatchesTheMigration() throws IOException {
        List<String> errors = MigrationSchemaChecker.check(ENTITIES, readMigration());
        assertTrue(errors.isEmpty(), "Mapping problems:\n - " + String.join("\n - ", errors));
    }
}
