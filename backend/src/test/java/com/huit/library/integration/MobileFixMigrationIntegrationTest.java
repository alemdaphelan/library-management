package com.huit.library.integration;

import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class MobileFixMigrationIntegrationTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine");

    @Test
    void upgradesLegacyDevicesAndProposalsAndRequiresExplicitDemoContext() throws Exception {
        var source =
                new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE push_device_registrations(id bigint primary key, token text)");
        jdbc.execute("INSERT INTO push_device_registrations VALUES(1,'old-token')");
        jdbc.execute("CREATE TABLE academic_majors(major_id varchar(128) primary key)");
        jdbc.execute("INSERT INTO academic_majors VALUES('CNTT')");
        jdbc.execute("CREATE TABLE book_proposals(proposal_id bigint primary key, genre text)");
        jdbc.execute("INSERT INTO book_proposals VALUES(1,'Legacy category')");
        migrate(source, "41-push-registration-identifier-type.yaml", "");
        migrate(source, "42-proposal-academic-major.yaml", "");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT identifier_type FROM push_device_registrations",
                                String.class))
                .isEqualTo("TOKEN");
        assertThat(jdbc.queryForObject("SELECT major_id FROM book_proposals", String.class))
                .isNull();
        assertThatThrownBy(() -> jdbc.update("UPDATE book_proposals SET major_id='FAKE'"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        jdbc.update(
                "UPDATE book_proposals SET major_id='CNTT',major_name='Snapshot',major_source_reference='Official catalog'");
        jdbc.execute(
                "CREATE TABLE books(book_id bigint primary key, default_price numeric, price_source text, price_status text, price_currency text, price_updated_at timestamp, is_digital boolean)");
        jdbc.execute(
                "INSERT INTO books VALUES(1,150000,'CONFIGURED_FALLBACK','ESTIMATED','VND',now(),false),(2,150000,'CATALOG','KNOWN','VND',now(),false),(3,0,'DIGITAL','KNOWN','VND',now(),true)");
        jdbc.execute("CREATE TABLE transactions(amount numeric)");
        jdbc.execute("INSERT INTO transactions VALUES(50000)");
        migrate(source, "43-demo-catalog-prices.yaml", "");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT default_price FROM books WHERE book_id=1", Integer.class))
                .isEqualTo(150000);
        migrate(source, "43-demo-catalog-prices.yaml", "demo");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT default_price FROM books WHERE book_id=1", Integer.class))
                .isBetween(15000, 65000);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT price_source FROM books WHERE book_id=1", String.class))
                .isEqualTo("DEMO_GENERATED");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT default_price FROM books WHERE book_id=2", Integer.class))
                .isEqualTo(150000);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT default_price FROM books WHERE book_id=3", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT amount FROM transactions", Integer.class))
                .isEqualTo(50000);
        migrate(source, "43-demo-catalog-prices.yaml", "demo");
        assertThat(jdbc.queryForObject("SELECT major_name FROM book_proposals", String.class))
                .isEqualTo("Snapshot");
    }

    void migrate(DriverManagerDataSource source, String file, String context) throws Exception {
        try (var c = source.getConnection()) {
            var db =
                    DatabaseFactory.getInstance()
                            .findCorrectDatabaseImplementation(new JdbcConnection(c));
            try (var lb =
                    new Liquibase(
                            "db/changelog/changesets/" + file,
                            new ClassLoaderResourceAccessor(),
                            db)) {
                lb.update(new Contexts(context), new LabelExpression());
            }
        }
    }
}
