package com.huit.library.integration;

import liquibase.*;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
class FinanceStockAnalyticsMigrationIntegrationTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:15-alpine");
    static DriverManagerDataSource source;
    static JdbcTemplate jdbc;
    static long legacyPaid,legacyUnpaid,receipt;
    @BeforeAll static void setup() throws Exception {
        source=new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());jdbc=new JdbcTemplate(source);
        migrate("db/changelog/dashboard-analytics-test-master.yaml",false);
        legacyPaid=jdbc.queryForObject("INSERT INTO transactions(type,amount,paid_amount,payment_method,status) VALUES ('FINE',200000,50000,'CASH','PARTIALLY_PAID') RETURNING transaction_id",Long.class);
        legacyUnpaid=jdbc.queryForObject("INSERT INTO transactions(type,amount,paid_amount,status) VALUES ('REPAIR_FEE',200000,0,'OUTSTANDING') RETURNING transaction_id",Long.class);
        receipt=jdbc.queryForObject("INSERT INTO goods_receipts(status,created_at) VALUES ('COMPLETED','2026-01-01') RETURNING receipt_id",Long.class);
        migrate("db/changelog/changesets/35-finance-import-analytics-evidence.yaml",false);
    }
    static void migrate(String master,boolean rollback) throws Exception {
        try(var c=source.getConnection()) {
            var db=DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));
            try(var lb=new Liquibase(master,new ClassLoaderResourceAccessor(),db)) {
                if(rollback)lb.rollback(1,new Contexts(),new LabelExpression());else lb.update(new Contexts(),new LabelExpression());
            }
        }
    }
    @Test void migrationDoesNotInventDepositProvenanceOrCompletionDate() {
        assertThat(jdbc.queryForObject("SELECT deposit_applied_amount FROM transactions WHERE transaction_id=?",java.math.BigDecimal.class,legacyPaid)).isNull();
        assertThat(jdbc.queryForObject("SELECT deposit_applied_amount FROM transactions WHERE transaction_id=?",java.math.BigDecimal.class,legacyUnpaid)).isZero();
        assertThat(jdbc.queryForObject("SELECT completed_at FROM goods_receipts WHERE receipt_id=?",java.sql.Timestamp.class,receipt)).isNull();
    }
    @Test void rollbackAndReapplyPreserveOldLedgerAndReceiptRows() throws Exception {
        // Reapply at the end so this test is independent of execution order.
        migrate("db/changelog/changesets/35-finance-import-analytics-evidence.yaml",true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_name='transactions' AND column_name='deposit_applied_amount'",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT amount FROM transactions WHERE transaction_id=?",java.math.BigDecimal.class,legacyPaid)).isEqualByComparingTo("200000");
        assertThat(jdbc.queryForObject("SELECT status FROM goods_receipts WHERE receipt_id=?",String.class,receipt)).isEqualTo("COMPLETED");
        migrate("db/changelog/changesets/35-finance-import-analytics-evidence.yaml",false);
        assertThat(jdbc.queryForObject("SELECT deposit_applied_amount FROM transactions WHERE transaction_id=?",java.math.BigDecimal.class,legacyPaid)).isNull();
    }
}
