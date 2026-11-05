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
class AcademicCatalogRepairMigrationIntegrationTest {
    @Container static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:15-alpine");

    @Test void restoresOnlySourcedMissingMajorsAndKeepsExistingManualIds() throws Exception {
        var source = new DriverManagerDataSource(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE academic_majors(major_id varchar(128) primary key, faculty_id varchar(64), major_name varchar(255), major_code varchar(32), source_reference varchar(1000), UNIQUE(faculty_id,major_name))");
        jdbc.execute("CREATE TABLE academic_department_mappings(department_value text primary key, faculty_id text, major_name text)");
        jdbc.update("INSERT INTO academic_majors VALUES ('manual-it','FIT','IT','manual-code','Manual source')");
        jdbc.update("INSERT INTO academic_department_mappings VALUES ('7480201','FIT','IT'),('IT alias','FIT','IT'),('7340301','ACC','Accounting'),('empty','FIT','')");
        for (int i = 0; i < 2; i++) {
            try (var c = source.getConnection()) {
                var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(c));
                try (var lb = new Liquibase("db/changelog/changesets/44-repair-official-major-catalog.yaml", new ClassLoaderResourceAccessor(), database)) {
                    lb.update(new Contexts(), new LabelExpression());
                }
            }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_majors", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT major_id FROM academic_majors WHERE major_name='IT'", String.class)).isEqualTo("manual-it");
        assertThat(jdbc.queryForObject("SELECT source_reference FROM academic_majors WHERE major_name='IT'", String.class)).isEqualTo("Manual source");
        assertThat(jdbc.queryForObject("SELECT major_code FROM academic_majors WHERE major_name='Accounting'", String.class)).isEqualTo("7340301");
        assertThat(jdbc.queryForObject("SELECT source_reference FROM academic_majors WHERE major_name='Accounting'", String.class)).contains("academic_department_mappings");
    }
}
