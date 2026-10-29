package com.huit.library.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@JdbcTest(properties = {
        "spring.liquibase.enabled=true",
        "spring.liquibase.change-log=classpath:db/changelog/library-policy-test-master.yaml"
})
class LibraryPolicyMigrationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("library_policy_migration_test")
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
    void migrationCreatesTheEditableDefaultPolicy() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT maximum_open_items FROM library_policies WHERE policy_id = 1",
                Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT home_loan_days FROM library_policies WHERE policy_id = 1",
                Integer.class)).isEqualTo(10);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT maximum_renewal_count FROM library_policies WHERE policy_id = 1",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT home_late_fine_per_day FROM library_policies WHERE policy_id = 1",
                BigDecimal.class)).isEqualByComparingTo("1000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT technical_processing_fee FROM library_policies WHERE policy_id = 1",
                BigDecimal.class)).isEqualByComparingTo("50000");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM library_policy_history WHERE policy_id = 1",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT maximum_renewal_count FROM library_policy_history WHERE policy_id = 1",
                Integer.class)).isEqualTo(1);
    }
}
