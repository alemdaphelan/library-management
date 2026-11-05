package com.huit.library.integration;

import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class ReaderLossReportMigrationIntegrationTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine");
    @Test void newColumnsAreNullableForLegacyLoansAndRequireACompleteReportWithSafeRollback() throws Exception {
        var source = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        var jdbc = new JdbcTemplate(source);
        migrate(source, "db/changelog/circulation-test-master.yaml", false);
        long ticket = jdbc.queryForObject("INSERT INTO loan_tickets(status) VALUES ('BORROWING') RETURNING ticket_id", Long.class);
        long detail = jdbc.queryForObject("INSERT INTO loan_details(ticket_id,return_status) VALUES (?,'BORROWING') RETURNING detail_id", Long.class, ticket);
        migrate(source, "db/changelog/changesets/37-reader-loss-reports.yaml", false);
        assertThat(jdbc.queryForObject("SELECT loss_reported_at FROM loan_details WHERE detail_id=?", java.sql.Timestamp.class, detail)).isNull();
        assertThat(jdbc.queryForObject("SELECT loss_report_description FROM loan_details WHERE detail_id=?", String.class, detail)).isNull();
        assertThatThrownBy(() -> jdbc.update("UPDATE loan_details SET loss_reported_at=CURRENT_TIMESTAMP WHERE detail_id=?", detail))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE loan_details SET loss_reported_at=CURRENT_TIMESTAMP,loss_report_description='  ' WHERE detail_id=?", detail))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE loan_details SET loss_reported_at=CURRENT_TIMESTAMP,loss_report_description='Mất sách trên xe buýt' WHERE detail_id=?", detail);
        assertThat(jdbc.queryForObject("SELECT return_status FROM loan_details WHERE detail_id=?", String.class, detail)).isEqualTo("BORROWING");
        migrate(source, "db/changelog/changesets/37-reader-loss-reports.yaml", true);
        assertThat(jdbc.queryForObject("SELECT return_status FROM loan_details WHERE detail_id=?", String.class, detail)).isEqualTo("BORROWING");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name='loan_details' AND column_name='loss_reported_at'", Long.class)).isZero();
        migrate(source, "db/changelog/changesets/37-reader-loss-reports.yaml", false);
        assertThat(jdbc.queryForObject("SELECT loss_reported_at FROM loan_details WHERE detail_id=?", java.sql.Timestamp.class, detail)).isNull();
    }
    void migrate(DriverManagerDataSource source, String path, boolean rollback) throws Exception {
        try (var connection = source.getConnection()) {
            var db = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var lb = new Liquibase(path, new ClassLoaderResourceAccessor(), db)) {
                if (rollback) lb.rollback(1, new Contexts(), new LabelExpression());
                else lb.update(new Contexts(), new LabelExpression());
            }
        }
    }
}
