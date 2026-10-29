package com.huit.library.integration;

import com.huit.library.modules.reporting.dto.StudentObligationReport;
import com.huit.library.modules.reporting.dto.StudentObligationReport.*;
import com.huit.library.modules.reporting.repository.StudentObligationReportRepository;
import com.huit.library.modules.reporting.service.StudentObligationExcelExporter;
import com.huit.library.modules.reporting.service.StudentObligationReportService;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@Testcontainers
@JdbcTest(properties = {"spring.liquibase.enabled=true", "spring.liquibase.change-log=classpath:db/changelog/circulation-test-master.yaml"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({StudentObligationReportRepository.class, StudentObligationReportService.class, StudentObligationExcelExporter.class})
@Transactional(isolation = Isolation.REPEATABLE_READ)
class StudentObligationReportIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("obligation_report_test").withUsername("test").withPassword("test");
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired StudentObligationReportService service;
    @Autowired StudentObligationExcelExporter exporter;
    private final LocalDateTime now = LocalDateTime.now(ZoneId.of(StudentObligationReportService.TIMEZONE));

    @BeforeEach
    void mapping() {
        jdbc.update("INSERT INTO academic_faculties VALUES ('TEST_IT', 'Khoa kiểm thử CNTT'), ('TEST_OTHER', 'Khoa kiểm thử khác')");
        jdbc.update("INSERT INTO academic_department_mappings VALUES ('Ngành kiểm thử phần mềm', 'TEST_IT', 'Kỹ thuật phần mềm')");
    }

    @Test
    void separatesOverdueDebtAndUnionIncludingReturnedReadersWithoutMultiplyingMoney() {
        UUID clear = student("01", "Ngành kiểm thử phần mềm"), overdue = student("02", null);
        UUID debt = student("03", "Ngành kiểm thử phần mềm"), both = student("04", "Không xác định");
        loan(clear, now.plusDays(1), "BORROWING");
        loan(overdue, now.minusDays(2), "BORROWING");
        loan(debt, now.minusDays(5), "RETURNED");
        charge(debt, 100, 25, "PARTIALLY_PAID"); wallet(debt, 75);
        loan(both, now.minusDays(3), "OVERDUE"); loan(both, now.minusDays(1), "BORROWING");
        charge(both, 200, 0, "OUTSTANDING"); charge(both, 50, 0, "OUTSTANDING"); wallet(both, 250);
        assertThat(ids(service.preview(Mode.OVERDUE, null, "", 0, 25))).containsExactly(overdue, both);
        var debts = service.preview(Mode.DEBT, null, "", 0, 25);
        assertThat(ids(debts)).containsExactly(debt, both);
        assertThat(debts.students().get(1).chargedAmount()).isEqualByComparingTo("250");
        assertThat(debts.students().get(1).overdueItemCount()).isEqualTo(2);
        assertThat(debts.students().get(0).overdueItemCount()).isZero();
        assertThat(debts.students().get(0).paidOrDepositAmount()).isEqualByComparingTo("25");
        assertThat(ids(service.preview(Mode.EITHER, null, "", 0, 25))).containsExactly(overdue, debt, both);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions", Long.class)).isEqualTo(3);
    }

    @Test
    void reconcilesPartialPaymentsDepositWaiverAndAdjustmentWithoutCountingCashTwice() {
        UUID user = student("money", "Ngành kiểm thử phần mềm");
        long original = charge(user, 200000, 100000, "PARTIALLY_WAIVED");
        // paid_amount: 50k deposit + 20k cash + 20k waiver + 10k adjustment.
        long payment = reduction(user, -20000, "PAYMENT", null, "COMPLETED");
        jdbc.update("INSERT INTO payment_allocations(payment_transaction_id,debt_transaction_id,amount) VALUES (?,?,20000)", payment, original);
        reduction(user, -20000, "WAIVER_ADJUSTMENT", null, "COMPLETED");
        reduction(user, -10000, "ADJUSTMENT", original, "COMPLETED");
        reduction(user, -90000, "ADJUSTMENT", original, "PENDING"); // not an approved adjustment
        reduction(user, -300000, "DEPOSIT_TOPUP", null, "COMPLETED"); // not a debt reduction
        wallet(user, 100000);
        var report = service.preview(Mode.DEBT, "TEST_IT", "money", 0, 25);
        var row = report.students().get(0);
        assertThat(row.chargedAmount()).isEqualByComparingTo("200000");
        assertThat(row.paidOrDepositAmount()).isEqualByComparingTo("70000");
        assertThat(row.waivedAmount()).isEqualByComparingTo("20000");
        assertThat(row.adjustedAmount()).isEqualByComparingTo("10000");
        assertThat(row.outstandingAmount()).isEqualByComparingTo("100000");
        assertThat(row.walletDifference()).isEqualByComparingTo("0");
        assertThat(row.reconciliationDifference()).isEqualByComparingTo("0");
        assertThat(report.ledgerEntries()).hasSize(4);
    }

    @Test
    void usesExplicitFacultyMappingAndPreservesUnknownDepartmentAndMissingWallet() {
        UUID mapped = student("mapped", " Ngành kiểm thử phần mềm "), raw = student("raw", "Khoa CNTT chưa xác minh");
        UUID blank = student("blank", null);
        loan(mapped, now.minusDays(1), "BORROWING"); loan(raw, now.minusDays(1), "BORROWING"); loan(blank, now.minusDays(1), "BORROWING");
        var found = service.preview(Mode.EITHER, "TEST_IT", "", 0, 25).students();
        assertThat(found).hasSize(1);
        assertThat(found.get(0).facultyId()).isEqualTo("TEST_IT");
        assertThat(found.get(0).majorName()).isEqualTo("Kỹ thuật phần mềm");
        assertThat(found.get(0).walletDebt()).isNull();
        var unknown = service.preview(Mode.EITHER, "UNCLASSIFIED", "", 0, 25).students();
        assertThat(unknown).hasSize(2).allMatch(row -> row.mappingStatus().equals("UNMAPPED"));
        assertThat(unknown).extracting(Student::rawDepartment).containsNull().contains("Khoa CNTT chưa xác minh");
        assertThat(service.faculties()).extracting(Faculty::facultyId).contains("TEST_IT", "TEST_OTHER", "UNCLASSIFIED");
    }

    @Test
    void ignoresTerminalIncidentPendingAndNotYetDueItemsEvenWithAnOpenTicket() {
        UUID user = student("states", null);
        for (String status : List.of("PENDING", "CANCELLED", "LOST", "DAMAGED", "SETTLED", "RETURNED")) loan(user, now.minusDays(10), status);
        loan(user, now.plusDays(1), "BORROWING");
        long stopped = loan(user, now.minusDays(2), "BORROWING");
        jdbc.update("UPDATE loan_details SET fine_stopped_at=? WHERE detail_id=?", now.minusDays(1), stopped);
        assertThat(service.preview(Mode.EITHER, null, "", 0, 25).students()).isEmpty();
        assertThat(service.preview(Mode.DEBT, null, "", 0, 25).students()).isEmpty();
    }

    @Test
    void includesWalletOnlyDebtAsAVisibleReconciliationIssueAndNeverCallsItLedgerDebt() {
        UUID user = student("orphan-wallet", null);
        wallet(user, 500);
        var row = service.preview(Mode.DEBT, null, "", 0, 25).students().get(0);
        assertThat(row.outstandingAmount()).isEqualByComparingTo("0");
        assertThat(row.walletDebt()).isEqualByComparingTo("500");
        assertThat(row.walletDifference()).isEqualByComparingTo("500");
        assertThat(service.preview(Mode.OVERDUE, null, "", 0, 25).students()).isEmpty();
    }

    @Test
    void previewPagesButExportIncludesAllMatchingStudentsAndTheirDetails() throws Exception {
        for (String code : List.of("page-01", "page-02", "page-03")) {
            UUID user = student(code, "Ngành kiểm thử phần mềm");
            loan(user, now.minusDays(2), "BORROWING"); charge(user, 100, 0, "OUTSTANDING");
        }
        UUID staff = student("page-staff", null);
        jdbc.update("UPDATE users SET user_type='LIBRARIAN' WHERE user_id=?", staff);
        charge(staff, 200, 0, "OUTSTANDING");
        var preview = service.preview(Mode.EITHER, "TEST_IT", "page-", 1, 1);
        assertThat(preview.totalStudents()).isEqualTo(3);
        assertThat(preview.students()).hasSize(1);
        assertThat(preview.students().get(0).mssv()).isEqualTo("page-02");
        assertThat(preview.overdueItems()).hasSize(1);
        var all = service.exportData(Mode.EITHER, "TEST_IT", "page-");
        assertThat(all.students()).hasSize(3); assertThat(all.overdueItems()).hasSize(3); assertThat(all.ledgerEntries()).hasSize(3);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(exporter.export(all)))) {
            assertThat(book.getNumberOfSheets()).isEqualTo(3);
            assertThat(book.getSheet("Tong hop").getLastRowNum()).isEqualTo(7);
            assertThat(book.getSheet("Qua han").getLastRowNum()).isEqualTo(7);
            assertThat(book.getSheet("So no").getLastRowNum()).isEqualTo(7);
            assertThat(book.getSheet("Tong hop").getRow(5).getCell(10).getNumericCellValue()).isEqualTo(100);
            assertThat(book.getSheet("Tong hop").getRow(5).getCell(8).getLocalDateTimeCellValue().toLocalDate()).isEqualTo(now.minusDays(2).toLocalDate());
        }
    }

    @Test
    void emptyReportStillProducesAValidWorkbookAndLiteralSearchDoesNotUseSqlWildcards() throws Exception {
        UUID user = student("100%_safe", null); charge(user, 100, 0, "OUTSTANDING");
        assertThat(service.preview(Mode.DEBT, null, "%_", 0, 25).students()).hasSize(1);
        assertThat(service.preview(Mode.DEBT, null, "' OR 1=1 --", 0, 25).students()).isEmpty();
        var empty = service.exportData(Mode.DEBT, null, "absent");
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(exporter.export(empty)))) {
            for (var sheet : book) { assertThat(sheet.getLastRowNum()).isEqualTo(4); assertThat(sheet.getRow(4)).isNotNull(); }
        }
        assertThat(service.preview(Mode.DEBT, null, "", 99, 1).students()).isEmpty();
    }

    @Test
    void excelSourceTextCannotBecomeFormulasAndPreservesLargeAmounts() throws Exception {
        UUID user = student("=1+1", null);
        jdbc.update("UPDATE users SET full_name=? WHERE user_id=?", "@SUM(A1:A5)", user);
        charge(user, 100, 0, "OUTSTANDING");
        jdbc.update("UPDATE transactions SET amount=12345678901234567.89, reason=? WHERE user_id=?", "\t=HYPERLINK(\"bad\")", user);
        try (var book = new XSSFWorkbook(new ByteArrayInputStream(exporter.export(service.exportData(Mode.DEBT, null, ""))))) {
            var row = book.getSheet("Tong hop").getRow(5);
            assertThat(row.getCell(0).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("=1+1");
            assertThat(row.getCell(1).getCellType()).isEqualTo(CellType.STRING);
            assertThat(row.getCell(10).getStringCellValue()).isEqualTo("12345678901234567.89");
            for (var sheet : book) for (var r : sheet) for (var cell : r) assertThat(cell.getCellType()).isNotEqualTo(CellType.FORMULA);
        }
    }

    @Test
    void rejectsInvalidFiltersInsteadOfSilentlyFallingBack() {
        assertThatThrownBy(() -> service.preview(Mode.DEBT, "FAKE", "", 0, 25)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.preview(Mode.DEBT, null, "", -1, 25)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.preview(Mode.DEBT, null, "", 0, 201)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.exportData(Mode.DEBT, null, "x".repeat(101))).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void migrationRejectsAmbiguousMappingAndSeedsVerifiedHuitFacultyCatalog() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM academic_faculties", Long.class)).isEqualTo(19); // 17 HUIT + 2 fixtures
        assertThatThrownBy(() -> jdbc.update("INSERT INTO academic_department_mappings VALUES ('Ngành kiểm thử phần mềm', 'TEST_OTHER', NULL)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void classifiesEveryOfficialMajorByNameCodeAndAllRepositoryStudentSeedDepartments() {
        var majors = jdbc.queryForList("SELECT department_value, faculty_id, major_name FROM academic_department_mappings WHERE department_value ~ '^[0-9]{7}$'");
        assertThat(majors).hasSize(39);
        int index = 0;
        for (var major : majors) {
            for (String source : List.of((String) major.get("department_value"), (String) major.get("major_name"), "Ngành " + major.get("major_name"))) {
                UUID user = student("catalog-" + index++, source);
                charge(user, 100, 0, "OUTSTANDING");
                var row = service.preview(Mode.DEBT, null, "catalog-" + (index - 1), 0, 200).students().stream()
                        .filter(item -> item.userId().equals(user)).findFirst().orElseThrow();
                assertThat(row.mappingStatus()).as(source).isEqualTo("MAPPED");
                assertThat(row.facultyId()).as(source).isEqualTo(major.get("faculty_id"));
                assertThat(row.majorName()).as(source).isEqualTo(major.get("major_name"));
            }
        }
        for (String source : List.of("CNTT", "cntt", "Khoa Công nghệ thông tin", "  KHOA CONG NGHE THONG TIN  ")) {
            UUID user = student("seed-dept-" + index++, source); charge(user, 100, 0, "OUTSTANDING");
        }
        var seed = service.preview(Mode.DEBT, "FIT", "seed-dept-", 0, 25);
        assertThat(seed.students()).hasSize(4).allMatch(row -> "MAPPED".equals(row.mappingStatus()));
        assertThat(seed.students()).allMatch(row -> row.majorName() == null); // faculty alone is not evidence of a specific major
        var faculties = jdbc.queryForList("SELECT faculty_id, faculty_name FROM academic_faculties WHERE faculty_id NOT LIKE 'TEST_%'");
        assertThat(faculties).hasSize(17);
        for (var faculty : faculties) {
            UUID user = student("faculty-dept-" + index++, (String) faculty.get("faculty_name")); charge(user, 100, 0, "OUTSTANDING");
            var row = service.preview(Mode.DEBT, (String) faculty.get("faculty_id"), "faculty-dept-" + (index - 1), 0, 25).students().get(0);
            assertThat(row.facultyId()).isEqualTo(faculty.get("faculty_id"));
            assertThat(row.majorName()).isNull();
        }
        var coverage = service.classificationCoverage();
        assertThat(coverage.totalStudents()).isEqualTo(138); // 39 majors x 3 forms + 4 seed forms + 17 faculties
        assertThat(coverage.classifiedStudents()).isEqualTo(138);
        assertThat(coverage.complete()).isTrue();
        assertThat(coverage.unmappedDepartments()).isEmpty();
    }

    @Test
    void normalizesCaseAccentsUnicodeWhitespaceAndPunctuationWithoutFuzzyClassification() {
        var inputs = List.of("  NGON   NGU   ANH  ", "TÀI CHÍNH–NGÂN HÀNG", "Công nghệ dệt may",
                java.text.Normalizer.normalize("Công nghệ thông tin", java.text.Normalizer.Form.NFD));
        var expected = List.of("LANGUAGES", "FINANCE_ACCOUNTING", "TEXTILE", "FIT");
        for (int i = 0; i < inputs.size(); i++) {
            UUID user = student("normalize-" + i, inputs.get(i)); charge(user, 100, 0, "OUTSTANDING");
            var row = service.preview(Mode.DEBT, null, "normalize-" + i, 0, 25).students().get(0);
            assertThat(row.facultyId()).as(inputs.get(i)).isEqualTo(expected.get(i));
            assertThat(row.rawDepartment()).isEqualTo(inputs.get(i));
        }
        UUID unknown = student("not-fuzzy", "Câu lạc bộ công nghệ thông tin"); charge(unknown, 100, 0, "OUTSTANDING");
        assertThat(service.preview(Mode.DEBT, null, "not-fuzzy", 0, 25).students().get(0).mappingStatus()).isEqualTo("UNMAPPED");
    }

    @Test
    void normalizedMappingCannotAssignTheSameDepartmentToTwoFaculties() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO academic_department_mappings VALUES ('cong NGHE THONG TIN', 'TEST_OTHER', NULL)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void coverageAuditsAllStudentsIncludingThoseWithoutDebtAndNeverHidesMissingValues() {
        student("coverage-mapped", "CNTT");
        student("coverage-null", null);
        student("coverage-empty", "");
        student("coverage-unknown-1", "Ngành chưa được xác nhận");
        student("coverage-unknown-2", "Ngành chưa được xác nhận");
        var coverage = service.classificationCoverage();
        assertThat(coverage.totalStudents()).isEqualTo(5);
        assertThat(coverage.classifiedStudents()).isEqualTo(1);
        assertThat(coverage.unclassifiedStudents()).isEqualTo(4);
        assertThat(coverage.complete()).isFalse();
        assertThat(coverage.unmappedDepartments()).hasSize(3);
        assertThat(coverage.unmappedDepartments()).anyMatch(group -> group.rawDepartment() == null && group.studentCount() == 1);
        assertThat(coverage.unmappedDepartments()).anyMatch(group -> "Ngành chưa được xác nhận".equals(group.rawDepartment()) && group.studentCount() == 2);
        assertThat(service.preview(Mode.EITHER, null, "coverage-", 0, 25).students()).isEmpty(); // audit is not limited to debt/overdue cohort
    }

    private List<UUID> ids(StudentObligationReport report) { return report.students().stream().map(Student::userId).toList(); }
    private UUID student(String mssv, String department) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users(user_id,mssv,full_name,department,user_type,role_id) VALUES (?,?,?,?, 'STUDENT','STUDENT')", id, mssv, "Sinh viên " + mssv, department);
        return id;
    }
    private long loan(UUID user, LocalDateTime due, String state) {
        Long ticket = jdbc.queryForObject("INSERT INTO loan_tickets(user_id,ticket_type,status,version) VALUES (?, 'BORROW', 'BORROWING',0) RETURNING ticket_id", Long.class, user);
        return jdbc.queryForObject("INSERT INTO loan_details(ticket_id,barcode,return_status,due_date,return_date) VALUES (?,?,?,?,?) RETURNING detail_id",
                Long.class, ticket, "REPORT-" + UUID.randomUUID(), state, due, "RETURNED".equals(state) ? now.minusDays(1) : null);
    }
    private long charge(UUID user, long amount, long paid, String state) {
        return jdbc.queryForObject("INSERT INTO transactions(user_id,type,amount,paid_amount,status,created_at) VALUES (?, 'FINE',?,?,?,?) RETURNING transaction_id",
                Long.class, user, amount, paid, state, now);
    }
    private long reduction(UUID user, long amount, String type, Long parent, String status) {
        return jdbc.queryForObject("INSERT INTO transactions(user_id,type,amount,status,parent_transaction_id,created_at) VALUES (?,?,?,?,?,?) RETURNING transaction_id",
                Long.class, user, type, amount, status, parent, now);
    }
    private void wallet(UUID user, long debt) { jdbc.update("INSERT INTO wallets(user_id,total_debt,deposit_balance) VALUES (?,?,0)", user, debt); }
}
