package com.huit.library.integration;

import com.huit.library.modules.reporting.dto.ProposalSummaryReport.*;
import com.huit.library.modules.reporting.repository.*;
import com.huit.library.modules.reporting.service.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.io.ByteArrayInputStream;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@JdbcTest(properties = {"spring.liquibase.enabled=true", "spring.liquibase.change-log=classpath:db/changelog/proposal-summary-test-master.yaml"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProposalSummaryRepository.class, ProposalSummaryService.class, ProposalSummaryExcelExporter.class, StudentObligationReportRepository.class})
class ProposalSummaryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("proposal_summary_test").withUsername("test").withPassword("test");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", POSTGRES::getUsername); r.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired ProposalSummaryService service;
    @Autowired ProposalSummaryExcelExporter exporter;
    private UUID it, other;
    @BeforeEach void setup() {
        jdbc.update("INSERT INTO academic_faculties VALUES ('B08_IT', 'Khoa thử CNTT'), ('B08_OTHER', 'Khoa thử khác')");
        jdbc.update("INSERT INTO academic_department_mappings VALUES ('Ngành B08 IT', 'B08_IT', 'IT'), ('Ngành B08 khác', 'B08_OTHER', 'Khác')");
        it = user(" Ngành B08 IT "); other = user("Ngành B08 khác");
    }
    @Test void canonicalIsbnGroupsAcrossFacultiesCountsRepeatedPeopleAndKeepsOriginals() {
        long a = proposal(it, "Clean Code", "0-13-235088-2", "1", 2008, "PENDING", "2026-10-01T00:00:00");
        long b = proposal(other, " Clean Code ", "978 0132350884", "1", 2008, "APPROVED", "2026-10-02T14:00:00");
        long c = proposal(it, "Clean Code", "9780132350884", "1", 2008, "PENDING", "2026-10-03T23:59:59");
        var all = service.exportData(null, null, null, Status.ALL);
        assertThat(all.totalGroups()).isEqualTo(1); assertThat(all.totalProposals()).isEqualTo(3); assertThat(all.totalPeople()).isEqualTo(2);
        var g = all.groups().get(0);
        assertThat(g.canonicalIsbn()).isEqualTo("9780132350884"); assertThat(g.peopleCount()).isEqualTo(2);
        assertThat(g.faculties()).extracting(FacultyCount::proposalCount).containsExactly(2L, 1L);
        assertThat(g.sources()).extracting(Source::proposalId).containsExactly(a, b, c);
        var filtered = service.exportData("B08_IT", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-03"), Status.PENDING);
        assertThat(filtered.totalProposals()).isEqualTo(2); assertThat(filtered.totalPeople()).isEqualTo(1);
        assertThat(filtered.groups().get(0).faculties()).extracting(FacultyCount::facultyId).containsExactly("B08_IT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM book_proposals WHERE status = 'PENDING'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM books", Integer.class)).isZero();
    }
    @Test void doesNotMergeDifferentIsbnsEditionsOrInvalidIsbnWithMissingIsbn() {
        proposal(it, "Same title", "9780132350884", "1", 2008, "PENDING", "2026-10-01T12:00:00");
        proposal(it, "Same title", "9780201530827", "2", 2009, "PENDING", "2026-10-01T12:00:00");
        proposal(it, "Same title", null, "1", 2008, "PENDING", "2026-10-01T12:00:00");
        proposal(other, "  SAME   TITLE  ", null, "1", 2008, "PENDING", "2026-10-01T12:00:00");
        proposal(it, "Same title", null, "2", 2009, "PENDING", "2026-10-01T12:00:00");
        proposal(it, "Same title", "9780132350880", "1", 2008, "PENDING", "2026-10-01T12:00:00");
        proposal(it, "Same title", "9780132350880", "1", 2008, "PENDING", "2026-10-01T12:00:00");
        var r = service.exportData(null, null, null, Status.ALL);
        assertThat(r.totalGroups()).isEqualTo(6); assertThat(r.groups().get(0).proposalCount()).isEqualTo(2);
        assertThat(r.groups().get(0).identityType()).isEqualTo("METADATA");
        assertThat(r.groups().stream().filter(g -> g.identityType().equals("INVALID"))).hasSize(2);
    }
    @Test void incompleteMetadataStaysSeparateAndSignificantPunctuationIsPreserved() {
        for (int i = 0; i < 2; i++) proposal(it, "Incomplete", null, null, null, "PENDING", "2026-10-01T00:00:00");
        proposal(it, "C++", null, "1", 2026, "PENDING", "2026-10-01T00:00:00");
        proposal(it, "C#", null, "1", 2026, "PENDING", "2026-10-01T00:00:00");
        assertThat(service.exportData(null, null, null, Status.ALL).totalGroups()).isEqualTo(4);
    }
    @Test void conflictingEditionForSameIsbnIsSeparatedAndWarned() {
        proposal(it, "Conflict", "9780132350884", "1", 2008, "PENDING", "2026-10-01T00:00:00");
        proposal(other, "Conflict", "9780132350884", "2", 2009, "PENDING", "2026-10-01T00:00:00");
        var r = service.exportData(null, null, null, Status.ALL);
        assertThat(r.groups()).hasSize(2).allMatch(g -> g.warnings().stream().anyMatch(w -> w.contains("Cùng ISBN")));
    }
    @Test void dateBoundsStatusUnclassifiedAndMissingUserRemainVisible() {
        UUID unknown = user("Chưa ánh xạ B08");
        proposal(unknown, "Before", null, "1", 2026, "PENDING", "2026-09-30T23:59:59");
        proposal(unknown, "Start", null, "1", 2026, "PENDING", "2026-10-01T00:00:00");
        proposal(unknown, "End", null, "1", 2026, "REJECTED", "2026-10-02T23:59:59.999999");
        proposal(null, "After", null, "1", 2026, "PENDING", "2026-10-03T00:00:00");
        var r = service.exportData("UNCLASSIFIED", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-02"), Status.ALL);
        assertThat(r.totalProposals()).isEqualTo(2); assertThat(r.groups()).extracting(Group::title).containsExactlyInAnyOrder("Start", "End");
        var orphan = service.exportData(null, LocalDate.parse("2026-10-03"), null, Status.ALL);
        assertThat(orphan.totalPeople()).isZero(); assertThat(orphan.totalProposals()).isEqualTo(1);
    }
    @Test void previewPaginatesGroupsAndExcelExportsEveryOriginalAsSafeStrings() throws Exception {
        String[] special = {"=HYPERLINK(\"bad\")", "+SUM(1,2)", "-1+2", "@SUM(1,2)", "\t=1+1", "\r=2+2"};
        for (int i = 0; i < 12; i++) proposal(it, i < special.length ? special[i] : "Title " + i, null, "1", 2026, "PENDING", "2026-10-01T00:00:00");
        var preview = service.preview(null, null, null, Status.ALL, 0, 5);
        assertThat(preview.groups()).hasSize(5); assertThat(preview.totalGroups()).isEqualTo(12);
        assertThat(service.preview(null, null, null, Status.ALL, Integer.MAX_VALUE, 100).groups()).isEmpty();
        var r = service.exportData(null, null, null, Status.ALL);
        byte[] bytes = exporter.export(r);
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheet("Tong hop").getLastRowNum()).isEqualTo(16);
            assertThat(workbook.getSheet("De xuat goc").getLastRowNum()).isEqualTo(16);
            for (var sheet : workbook) for (var row : sheet) for (var cell : row) assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
            var formula = r.groups().stream().filter(g -> g.title().startsWith("=")).findFirst().orElseThrow();
            int index = r.groups().indexOf(formula) + 5;
            assertThat(workbook.getSheet("Tong hop").getRow(index).getCell(3).getStringCellValue()).startsWith("=HYPERLINK");
            assertThat(workbook.getSheet("Tong hop").getRow(index).getCell(8).getNumericCellValue()).isEqualTo(1);
        }
    }
    @Test void emptyWorkbookIsValidAndInvalidFiltersAreRejected() throws Exception {
        var r = service.exportData(null, null, null, Status.ALL);
        assertThat(r.totalGroups()).isZero();
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(exporter.export(r)))) {
            assertThat(book.getSheet("Tong hop").getLastRowNum()).isEqualTo(4);
            assertThat(book.getSheet("Tong hop").getRow(4).getCell(0).getStringCellValue()).isEqualTo("Mã nhóm");
        }
        assertThatThrownBy(() -> service.preview(null, null, null, Status.ALL, -1, 25)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.preview(null, null, null, Status.ALL, 0, 101)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.exportData("MISSING", null, null, Status.ALL)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.exportData(null, LocalDate.parse("2026-10-03"), LocalDate.parse("2026-10-01"), Status.ALL)).isInstanceOf(ResponseStatusException.class);
    }
    private UUID user(String department) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(user_id,full_name,mssv,department,user_type) VALUES (?,?,?,?, 'STUDENT')", id, "B08 test", id.toString().substring(0,8), department); return id;
    }
    private long proposal(UUID user, String title, String isbn, String edition, Integer year, String status, String at) {
        return jdbc.queryForObject("INSERT INTO book_proposals(user_id,title,author_name,publisher,isbn,edition,publication_year,status,created_at) VALUES (?,?, 'Tác giả', 'NXB',?,?,?,?,?) RETURNING proposal_id",
                Long.class, user, title, isbn, edition, year, status, LocalDateTime.parse(at));
    }
}
