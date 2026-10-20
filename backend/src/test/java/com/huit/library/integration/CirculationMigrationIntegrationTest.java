package com.huit.library.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@JdbcTest(properties = {
        "spring.liquibase.enabled=true",
        "spring.liquibase.change-log=classpath:db/changelog/circulation-test-master.yaml"
})
class CirculationMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("circulation_migration_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void migrationNormalizesLegacyValuesAndCreatesAuditSchema() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT ticket_type FROM loan_tickets LIMIT 1", String.class)).isEqualTo("BORROW");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM loan_tickets LIMIT 1", String.class)).isEqualTo("RETURNED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT return_status FROM loan_details LIMIT 1", String.class)).isEqualTo("RETURNED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'loan_status_history'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM loan_tickets LIMIT 1", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'circulation_operations'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'transactions' AND column_name = 'dedup_key'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'notifications' AND column_name = 'dedup_key'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = 'penalty_cases'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'loan_details' AND column_name = 'fine_stopped_at'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_name = 'digital_documents' AND column_name = 'storage_key'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE table_name = 'digital_documents'
                  AND constraint_name = 'chk_digital_documents_access_level'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT book_id FROM digital_documents WHERE document_id = 9001",
                Long.class)).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM digital_usage_logs WHERE document_id = 9001",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM digital_usage_logs WHERE document_id = 9998",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM recommendation_subjects", Integer.class)).isEqualTo(13);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM department_subject_affinities dsa
                JOIN recommendation_subjects rs ON rs.subject_id = dsa.subject_id
                WHERE dsa.department_key = 'khoa cong nghe thong tin'
                  AND rs.code = 'IT_DATA_AI'
                  AND dsa.weight = 1.000
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                        "WHERE table_name = 'book_subject_assignments'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    void databaseRejectsUnknownCirculationStatus() {
        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO loan_tickets (ticket_type, status, version) VALUES ('BORROW', 'UNKNOWN', 0)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateIdempotencyKey() {
        jdbcTemplate.update("""
                INSERT INTO circulation_operations (
                    operation_id, idempotency_key, operation_type, request_hash,
                    ticket_ids, barcodes, result_status, message)
                VALUES (?, 'same-key', 'CHECKOUT', ?, '1', 'BC-1', 'BORROWING', 'ok')
                """, UUID.randomUUID(), "a".repeat(64));

        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO circulation_operations (
                    operation_id, idempotency_key, operation_type, request_hash,
                    ticket_ids, barcodes, result_status, message)
                VALUES (?, 'same-key', 'CHECKOUT', ?, '2', 'BC-2', 'BORROWING', 'ok')
                """, UUID.randomUUID(), "b".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsDuplicateNotificationDedupKeys() {
        jdbcTemplate.update("""
                INSERT INTO notifications (notif_id, type, content, dedup_key)
                VALUES (?, 'LOAN_DUE_SOON', 'first', 'notice-key')
                """, UUID.randomUUID());
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO notifications (notif_id, type, content, dedup_key)
                VALUES (?, 'LOAN_DUE_SOON', 'second', 'notice-key')
                """, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

    }

    @Test
    void databaseRejectsDuplicateFineDedupKeys() {
        jdbcTemplate.update("""
                INSERT INTO transactions (amount, type, dedup_key)
                VALUES (1000, 'FINE', 'fine-key')
                """);
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO transactions (amount, type, dedup_key)
                VALUES (1000, 'FINE', 'fine-key')
                """))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
