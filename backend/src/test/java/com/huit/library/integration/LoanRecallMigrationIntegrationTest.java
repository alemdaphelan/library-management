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
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class LoanRecallMigrationIntegrationTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:15-alpine");
    @Test void legacyFlagsAreNotFabricatedAndNewEvidenceCannotExtendAnOldDeadline()throws Exception{
        var source=new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());var jdbc=new JdbcTemplate(source);
        migrate(source,"db/changelog/dashboard-analytics-test-master.yaml",false);
        long ticket=jdbc.queryForObject("INSERT INTO loan_tickets(status) VALUES ('BORROWING') RETURNING ticket_id",Long.class);
        long detail=jdbc.queryForObject("INSERT INTO loan_details(ticket_id,return_status,is_recalled,due_date) VALUES (?,'BORROWING',true,'2026-11-09 10:00') RETURNING detail_id",Long.class,ticket);
        migrate(source,"db/changelog/changesets/38-loan-recall-evidence.yaml",false);
        assertThat(jdbc.queryForObject("SELECT recall_requested_at FROM loan_details WHERE detail_id=?",java.sql.Timestamp.class,detail)).isNull();
        assertThat(jdbc.queryForObject("SELECT is_recalled FROM loan_details WHERE detail_id=?",Boolean.class,detail)).isTrue();
        assertThatThrownBy(()->jdbc.update("UPDATE loan_details SET recall_requested_at='2026-11-04' WHERE detail_id=?",detail)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("UPDATE loan_details SET recall_requested_at='2026-11-04',recall_requested_by=?,recall_reason='Cần sách',recall_previous_due_date=due_date,recall_due_date='2026-11-11' WHERE detail_id=?",UUID.randomUUID(),detail)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE loan_details SET recall_requested_at='2026-11-04',recall_requested_by=?,recall_reason='Cần sách',recall_previous_due_date=due_date,recall_due_date=due_date WHERE detail_id=?",UUID.randomUUID(),detail);
        migrate(source,"db/changelog/changesets/38-loan-recall-evidence.yaml",true);
        assertThat(jdbc.queryForObject("SELECT due_date::text FROM loan_details WHERE detail_id=?",String.class,detail)).startsWith("2026-11-09");
        assertThat(jdbc.queryForObject("SELECT is_recalled FROM loan_details WHERE detail_id=?",Boolean.class,detail)).isTrue();
        migrate(source,"db/changelog/changesets/38-loan-recall-evidence.yaml",false);
        assertThat(jdbc.queryForObject("SELECT recall_reason FROM loan_details WHERE detail_id=?",String.class,detail)).isNull();
        migrate(source,"db/changelog/changesets/29-library-policy.yaml",false);
        migrate(source,"db/changelog/changesets/30-library-policy-renewal-limit.yaml",false);
        migrate(source,"db/changelog/changesets/31-library-policy-history.yaml",false);
        migrate(source,"db/changelog/changesets/38-security-deposit-recall-debt-policy.yaml",false);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema=current_schema() AND table_name='loan_details'
                  AND column_name IN ('recall_requested_at','recall_requested_by','recall_reason',
                    'recall_previous_due_date','recall_due_date','recalled_at','recall_due_at','original_due_date_before_recall')
                """,Long.class)).isEqualTo(8L);
        assertThat(jdbc.queryForObject("SELECT recall_return_days FROM library_policies WHERE policy_id=1",Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT is_recalled FROM loan_details WHERE detail_id=?",Boolean.class,detail)).isTrue();
    }
    void migrate(DriverManagerDataSource source,String path,boolean rollback)throws Exception{try(var c=source.getConnection()){var db=DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));try(var lb=new Liquibase(path,new ClassLoaderResourceAccessor(),db)){if(rollback)lb.rollback(1,new Contexts(),new LabelExpression());else lb.update(new Contexts(),new LabelExpression());}}}
}
