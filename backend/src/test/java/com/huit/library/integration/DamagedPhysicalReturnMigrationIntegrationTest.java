package com.huit.library.integration;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class DamagedPhysicalReturnMigrationIntegrationTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine");

    @Test
    void reconcilesOnlyProvenReceiptsWithoutClosingUnreceivedItemsOrTouchingMoney()
            throws Exception {
        var source =
                new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        var jdbc = new JdbcTemplate(source);
        migrate(source, "db/changelog/dashboard-analytics-test-master.yaml");
        long complete = ticket(jdbc), partial = ticket(jdbc), unknown = ticket(jdbc);
        long damaged = detail(jdbc, complete, "DAMAGED", "2026-10-31 10:22:53", null);
        long settled =
                detail(jdbc, complete, "SETTLED", "2026-10-30 09:00:00", "2026-11-02 12:00:00");
        detail(jdbc, partial, "DAMAGED", "2026-10-31 10:22:53", null);
        detail(jdbc, partial, "BORROWING", null, null);
        detail(jdbc, partial, "LOST", null, null);
        long unproven = detail(jdbc, unknown, "DAMAGED", null, null);
        // Financial state deliberately remains independent of receipt reconciliation.
        jdbc.update(
                "INSERT INTO wallets(user_id,deposit_balance,total_debt) VALUES (gen_random_uuid(),0,200000)");
        migrate(source, "db/changelog/changesets/36-damaged-physical-return.yaml");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM loan_tickets WHERE ticket_id=?",
                                String.class,
                                complete))
                .isEqualTo("RETURNED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM loan_details WHERE detail_id IN (?,?) AND return_date=physical_return_at",
                                Long.class,
                                damaged,
                                settled))
                .isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM loan_tickets WHERE ticket_id=?",
                                String.class,
                                partial))
                .isEqualTo("BORROWING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM loan_tickets WHERE ticket_id=?",
                                String.class,
                                unknown))
                .isEqualTo("BORROWING");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT physical_return_at FROM loan_details WHERE detail_id=?",
                                java.sql.Timestamp.class,
                                unproven))
                .isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT return_date FROM loan_details WHERE detail_id=?",
                                java.sql.Timestamp.class,
                                unproven))
                .isNull();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT total_debt FROM wallets", java.math.BigDecimal.class))
                .isEqualByComparingTo("200000");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM loan_status_history WHERE reason LIKE 'F03:%'",
                                Long.class))
                .isEqualTo(1);
        // Liquibase replay is a no-op; it cannot replace receipt times or add duplicate history.
        migrate(source, "db/changelog/changesets/36-damaged-physical-return.yaml");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM loan_status_history WHERE reason LIKE 'F03:%'",
                                Long.class))
                .isEqualTo(1);
    }

    private long ticket(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "INSERT INTO loan_tickets(status) VALUES ('BORROWING') RETURNING ticket_id",
                Long.class);
    }

    private long detail(
            JdbcTemplate jdbc, long ticket, String state, String physical, String returned) {
        return jdbc.queryForObject(
                "INSERT INTO loan_details(ticket_id,return_status,physical_return_at,return_date) VALUES (?,?,?::timestamp,?::timestamp) RETURNING detail_id",
                Long.class,
                ticket,
                state,
                physical,
                returned);
    }

    private void migrate(DriverManagerDataSource source, String path) throws Exception {
        try (var connection = source.getConnection()) {
            var db =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var lb = new Liquibase(path, new ClassLoaderResourceAccessor(), db)) {
                lb.update(new Contexts(), new LabelExpression());
            }
        }
    }
}
