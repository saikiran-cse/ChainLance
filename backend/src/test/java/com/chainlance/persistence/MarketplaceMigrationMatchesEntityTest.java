package com.chainlance.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chainlance.entity.NegotiationOffer;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Checks V2__marketplace_core.sql against the new entity without a database, using the same checker as the V1 test.
 * (The V1 test is left exactly as it was: it still checks V1 against the 16 original entities.)
 */
class MarketplaceMigrationMatchesEntityTest {

    private static String readV2() throws IOException {
        try (InputStream in = MarketplaceMigrationMatchesEntityTest.class.getClassLoader()
                .getResourceAsStream("db/migration/V2__marketplace_core.sql")) {
            assertNotNull(in, "V2__marketplace_core.sql must be on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void offerEntityMatchesTheNewTable() throws IOException {
        List<String> errors = MigrationSchemaChecker.check(List.<Class<?>>of(NegotiationOffer.class), readV2());
        assertTrue(errors.isEmpty(), "Mapping problems:\n - " + String.join("\n - ", errors));
    }

    @Test
    void v2OnlyCreatesTheOfferTable() throws IOException {
        assertEquals(List.of("negotiation_offers"), List.copyOf(MigrationSchemaChecker.parse(readV2()).keySet()));
    }

    @Test
    void v2ReplacesThePlainProposalUniquenessWithAnActiveProposalKey() throws IOException {
        String sql = readV2();
        assertTrue(sql.contains("DROP INDEX uq_proposal_project_freelancer"));
        assertTrue(sql.contains("uq_proposal_one_active"));
        // the foreign key needs an index on project_id, so it must be created BEFORE the old unique index is dropped
        assertTrue(sql.indexOf("ix_proposals_project") < sql.indexOf("DROP INDEX uq_proposal_project_freelancer"));
    }

    @Test
    void databaseItselfAllowsOnlyOnePendingAndOneAcceptedOfferPerProposal() throws IOException {
        String sql = readV2();
        assertTrue(sql.contains("UNIQUE KEY uq_offer_one_pending (pending_key)"));
        assertTrue(sql.contains("UNIQUE KEY uq_offer_one_accepted (accepted_key)"));
    }
}
