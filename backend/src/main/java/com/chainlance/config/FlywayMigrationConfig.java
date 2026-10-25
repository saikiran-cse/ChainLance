package com.chainlance.config;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Self-healing Flyway migration strategy.
 * If an initial deployment failed halfway (leaving success=0 in flyway_schema_history),
 * this strategy automatically cleans up the broken partial tables so Flyway can re-run
 * all migrations cleanly from scratch without requiring manual database intervention.
 */
@Configuration
public class FlywayMigrationConfig {

    private static final Logger log = LoggerFactory.getLogger(FlywayMigrationConfig.class);

    private static final String[] TABLES_TO_DROP = {
        "flyway_schema_history",
        "negotiation_offers",
        "reviews",
        "dispute_responses",
        "disputes",
        "settlement_proposals",
        "deadline_extension_requests",
        "blockchain_transactions",
        "submissions",
        "milestones",
        "agreements",
        "negotiation_messages",
        "proposals",
        "project_milestone_plans",
        "projects",
        "wallet_challenges",
        "wallet_addresses",
        "users"
    };

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try (Connection conn = flyway.getConfiguration().getDataSource().getConnection();
                 Statement stmt = conn.createStatement()) {

                boolean hasFailedMigration = false;
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_schema = DATABASE() AND table_name = 'flyway_schema_history'")) {
                    if (rs.next() && rs.getInt(1) > 0) {
                        try (ResultSet rsFailed = stmt.executeQuery(
                                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0")) {
                            if (rsFailed.next() && rsFailed.getInt(1) > 0) {
                                hasFailedMigration = true;
                            }
                        }
                    }
                }

                if (hasFailedMigration) {
                    log.warn("Detected previously failed Flyway migration in database. Resetting schema for clean initial deployment...");
                    stmt.execute("SET FOREIGN_KEY_CHECKS = 0");
                    for (String table : TABLES_TO_DROP) {
                        stmt.execute("DROP TABLE IF EXISTS " + table);
                    }
                    stmt.execute("SET FOREIGN_KEY_CHECKS = 1");
                    log.info("Schema cleanly reset.");
                }
            } catch (Exception e) {
                log.warn("Could not check/reset failed schema: {}", e.getMessage());
            }

            // Run normal migrations
            flyway.migrate();
        };
    }
}
