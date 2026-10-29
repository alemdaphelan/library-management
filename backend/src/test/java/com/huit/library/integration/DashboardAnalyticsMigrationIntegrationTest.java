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
class DashboardAnalyticsMigrationIntegrationTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:15-alpine");
    static DriverManagerDataSource source;static JdbcTemplate jdbc;static long actual,settled,pending,lateDetail;
    @BeforeAll static void migrate()throws Exception{
        source=new DriverManagerDataSource(pg.getJdbcUrl(),pg.getUsername(),pg.getPassword());jdbc=new JdbcTemplate(source);
        migration("public","db/changelog/circulation-test-master.yaml",0);
        long book=jdbc.queryForObject("INSERT INTO books(title) VALUES ('Legacy checkout evidence') RETURNING book_id",Long.class);
        jdbc.update("INSERT INTO book_copies(barcode,book_id,status) VALUES ('LEGACY-ANALYTICS',?,'AVAILABLE')",book);
        long ticket=jdbc.queryForObject("INSERT INTO loan_tickets(status) VALUES ('RETURNED') RETURNING ticket_id",Long.class);
        jdbc.update("INSERT INTO loan_status_history(ticket_id,from_status,to_status,created_at) VALUES (?,'RESERVED','BORROWING','2025-01-03')",ticket);
        actual=jdbc.queryForObject("INSERT INTO loan_details(ticket_id,barcode,return_status,created_at,return_date) VALUES (?,'LEGACY-ANALYTICS','RETURNED','2025-01-01','2025-01-10') RETURNING detail_id",Long.class,ticket);
        settled=jdbc.queryForObject("INSERT INTO loan_details(ticket_id,barcode,return_status,created_at,return_date) VALUES (?,'LEGACY-ANALYTICS','SETTLED','2025-01-01','2025-02-10') RETURNING detail_id",Long.class,ticket);
        pending=jdbc.queryForObject("INSERT INTO loan_details(ticket_id,barcode,return_status,created_at,due_date) VALUES (?,'LEGACY-ANALYTICS','PENDING','2025-01-01','2025-01-13') RETURNING detail_id",Long.class,ticket);
        lateDetail=jdbc.queryForObject("INSERT INTO loan_details(ticket_id,barcode,return_status,created_at) VALUES (?,'LEGACY-ANALYTICS','BORROWING','2025-02-01') RETURNING detail_id",Long.class,ticket);
        migration("public","db/changelog/dashboard-analytics-test-master.yaml",0);
    }
    static void migration(String schema,String master,int rollback)throws Exception{
        try(var connection=source.getConnection()){connection.setSchema(schema);var db=DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));db.setDefaultSchemaName(schema);
            try(var lb=new Liquibase(master,new ClassLoaderResourceAccessor(),db)){if(rollback>0)lb.rollback(rollback,new Contexts(),new LabelExpression());else lb.update(new Contexts(),new LabelExpression());}}
    }
    @Test void onlyProvenCheckoutAndNormalReturnAreBackfilled(){
        assertThat(jdbc.queryForObject("SELECT checkout_at::text FROM loan_details WHERE detail_id=?",String.class,actual)).startsWith("2025-01-03");
        assertThat(jdbc.queryForObject("SELECT checkout_time_source FROM loan_details WHERE detail_id=?",String.class,actual)).isEqualTo("LEGACY_BORROWING_HISTORY");
        assertThat(jdbc.queryForObject("SELECT physical_return_at::text FROM loan_details WHERE detail_id=?",String.class,actual)).startsWith("2025-01-10");
        assertThat(jdbc.queryForObject("SELECT physical_return_at FROM loan_details WHERE detail_id=?",java.sql.Timestamp.class,settled)).isNull();
        assertThat(jdbc.queryForObject("SELECT checkout_at FROM loan_details WHERE detail_id=?",java.sql.Timestamp.class,pending)).isNull();
        assertThat(jdbc.queryForObject("SELECT checkout_at FROM loan_details WHERE detail_id=?",java.sql.Timestamp.class,lateDetail)).isNull();
    }
    @Test void academicCatalogComesFromExplicitMappingsButDoesNotSeedAnyBookAssignments(){
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_majors WHERE faculty_id='FIT' AND major_name='Công nghệ thông tin'",Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT major_code FROM academic_majors WHERE faculty_id='FIT' AND major_name='Công nghệ thông tin'",String.class)).isEqualTo("7480201");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM material_major_assignments",Long.class)).isZero();
        assertThatThrownBy(()->jdbc.update("INSERT INTO material_major_assignments(assignment_id,profile_id,major_id) VALUES (?,?,?)",UUID.randomUUID(),UUID.randomUUID(),"INVALID")).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void rollbackAndReapplyLeaveOriginalLoansAndCatalogIntact()throws Exception{
        jdbc.execute("CREATE SCHEMA analytics_rollback");migration("analytics_rollback","db/changelog/dashboard-analytics-test-master.yaml",0);
        long before=jdbc.queryForObject("SELECT count(*) FROM analytics_rollback.loan_details",Long.class);
        migration("analytics_rollback","db/changelog/dashboard-analytics-test-master.yaml",2);
        assertThat(jdbc.queryForObject("SELECT to_regclass('analytics_rollback.material_classifications')",String.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='analytics_rollback' AND table_name='loan_details' AND column_name='checkout_at'",Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_rollback.loan_details",Long.class)).isEqualTo(before);
        migration("analytics_rollback","db/changelog/dashboard-analytics-test-master.yaml",0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM analytics_rollback.material_classification_history",Long.class)).isZero();
    }
}
