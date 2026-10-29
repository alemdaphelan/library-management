package com.huit.library.integration;

import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class DisposalMigrationIntegrationTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine");
    static DriverManagerDataSource source;
    static JdbcTemplate jdbc;
    @BeforeAll static void migrate() throws Exception {
        source = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()); jdbc = new JdbcTemplate(source);
        migration("public", false);
    }
    static void migration(String schema, boolean rollback) throws Exception {
        try (var connection = source.getConnection()) {
            connection.setSchema(schema);
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)); database.setDefaultSchemaName(schema);
            try (var lb = new Liquibase("db/changelog/disposal-test-master.yaml", new ClassLoaderResourceAccessor(), database)) {
                if (rollback) lb.rollback(1, new Contexts(), new LabelExpression()); else lb.update(new Contexts(), new LabelExpression());
            }
        }
    }
    @Test void migrationCreatesSeparateHistoryAndProtectsOriginalCopyAndAuditReferences() {
        var f = fixture(); UUID id = insert(f, "DRAFT");
        assertThat(jdbc.queryForObject("SELECT status FROM book_copies WHERE barcode=?",String.class,f.barcode())).isEqualTo("AVAILABLE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM book_proposals",Long.class)).isZero();
        assertThatThrownBy(() -> jdbc.update("DELETE FROM book_copies WHERE barcode=?",f.barcode())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM inventory_audits WHERE audit_id=?",f.audit())).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO disposal_history(event_id,proposal_id,proposal_version,event_type,actor_id,actor_name,after_snapshot) VALUES (?,?,0,'CREATED',?,'Test','{}')",UUID.randomUUID(),id,UUID.randomUUID());
        assertThatThrownBy(() -> jdbc.update("DELETE FROM disposal_proposals WHERE proposal_id=?",id)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void partialUniqueIndexAllowsOneActiveProposalAndRetainsCancelledHistory() {
        var f=fixture(); UUID first=insert(f,"DRAFT");
        assertThatThrownBy(() -> insert(f,"SUBMITTED")).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE disposal_proposals SET status='CANCELLED' WHERE proposal_id=?",first);
        insert(f,"DRAFT"); assertThat(jdbc.queryForObject("SELECT count(*) FROM disposal_proposals WHERE barcode=?",Long.class,f.barcode())).isEqualTo(2);
        assertThatThrownBy(() -> jdbc.update("UPDATE disposal_proposals SET status='APPROVED' WHERE proposal_id=?",first)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void rollbackAndReapplyOnlyRemoveTheNewModuleAndLeaveInventoryUntouched() throws Exception {
        jdbc.execute("CREATE SCHEMA b09_rollback"); migration("b09_rollback",false);
        long booksBefore=jdbc.queryForObject("SELECT count(*) FROM b09_rollback.book_copies",Long.class);
        migration("b09_rollback",true);
        assertThat(jdbc.queryForObject("SELECT to_regclass('b09_rollback.disposal_proposals')",String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM b09_rollback.book_copies",Long.class)).isEqualTo(booksBefore);
        migration("b09_rollback",false);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM b09_rollback.disposal_history",Long.class)).isZero();
    }
    record Fixture(UUID request, long book, String barcode, long audit, String scan) {}
    Fixture fixture() {
        UUID req=UUID.randomUUID(); long book=jdbc.queryForObject("INSERT INTO books(title) VALUES ('B09 migration') RETURNING book_id",Long.class);
        String barcode="MIG-"+UUID.randomUUID(),scan=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO book_copies(barcode,book_id,status) VALUES (?,?,'AVAILABLE')",barcode,book);
        long audit=jdbc.queryForObject("INSERT INTO inventory_audits(status) VALUES ('COMPLETED') RETURNING audit_id",Long.class);
        jdbc.update("INSERT INTO inventory_details(detail_id,audit_id,barcode,scan_status) VALUES (?,?,?,'FOUND')",scan,String.valueOf(audit),barcode);
        jdbc.update("INSERT INTO disposal_batches(request_id,created_by,request_payload) VALUES (?,?,'{}')",req,UUID.randomUUID());
        return new Fixture(req,book,barcode,audit,scan);
    }
    UUID insert(Fixture f,String status) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO disposal_proposals(proposal_id,request_id,barcode,book_id,book_title,audit_id,source_detail_id,source_fingerprint,source_snapshot,observed_condition,reason_code,reason,proposed_action,status,created_by,created_by_name) VALUES (?,?,?,?,?,?,?,?,?,'Cũ','WORN','Cũ','REPAIR',?,?,'Test')",
                id,f.request(),f.barcode(),f.book(),"B09 migration",f.audit(),f.scan(),"a".repeat(64),"{}",status,UUID.randomUUID());return id;
    }
}
