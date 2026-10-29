package com.huit.library.modules.reporting.repository;

import com.huit.library.modules.reporting.dto.StudentObligationReport.*;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class StudentObligationReportRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String CHARGES = "'FINE', 'REPAIR_FEE', 'COMPENSATION', 'TECHNICAL_FEE'";
    private static final String OPEN = "'OUTSTANDING', 'PARTIALLY_PAID', 'PARTIALLY_ADJUSTED', 'PARTIALLY_WAIVED'";
    // Same deterministic key as migration 30's unique index: NFC/NFD, case, accents and punctuation.
    // Only explicit catalog aliases are matched; no substring/subject-affinity guesses.
    static String departmentKey(String column) {
        return "btrim(regexp_replace(translate(lower(regexp_replace(normalize(" + column + ", NFD), "
                + "U&'[\\0300-\\036f]', '', 'g')), 'đ', 'd'), '[^a-z0-9]+', ' ', 'g'))";
    }
    private static final String OVERDUE = """
            FROM loan_details d JOIN loan_tickets t ON t.ticket_id = d.ticket_id
            WHERE d.return_date IS NULL AND d.return_status IN ('BORROWING', 'OVERDUE')
              AND t.status IN ('BORROWING', 'OVERDUE') AND d.fine_stopped_at IS NULL
              AND d.due_date < :snapshot
            """;
    // Each source is reduced to one row per user BEFORE joining; never join raw debts to raw loans.
    private static final String BASE = """
            WITH overdue AS (
              SELECT t.user_id, count(*) AS item_count, min(d.due_date) AS earliest_due
            """ + OVERDUE + """
              GROUP BY t.user_id
            ), charges AS (
              SELECT user_id, sum(amount) AS charged, sum(coalesce(paid_amount, 0)) AS satisfied,
                sum(CASE WHEN status IN (""" + OPEN + """
                  ) THEN greatest(amount - coalesce(paid_amount, 0), 0) ELSE 0 END) AS outstanding
              FROM transactions WHERE amount > 0 AND type IN (""" + CHARGES + """
              ) GROUP BY user_id
            ), reductions AS (
              SELECT user_id,
                sum(CASE WHEN type = 'WAIVER_ADJUSTMENT' THEN -amount ELSE 0 END) AS waived,
                sum(CASE WHEN type = 'ADJUSTMENT' THEN -amount ELSE 0 END) AS adjusted
              FROM transactions WHERE amount < 0 AND status = 'COMPLETED'
                AND type IN ('WAIVER_ADJUSTMENT', 'ADJUSTMENT') GROUP BY user_id
            ), wallets AS (
              SELECT user_id, sum(coalesce(total_debt, 0)) AS wallet_debt FROM wallets GROUP BY user_id
            ), students AS (
              SELECT u.user_id, u.mssv, u.full_name, u.department AS raw_department,
                coalesce(f.faculty_id, 'UNCLASSIFIED') AS faculty_id,
                coalesce(f.faculty_name, 'Chưa phân loại') AS faculty_name, m.major_name,
                CASE WHEN f.faculty_id IS NULL THEN 'UNMAPPED' ELSE 'MAPPED' END AS mapping_status,
                coalesce(o.item_count, 0) AS overdue_count, o.earliest_due,
                coalesce(c.charged, 0) AS charged, coalesce(c.satisfied, 0) AS satisfied,
                coalesce(r.waived, 0) AS waived, coalesce(r.adjusted, 0) AS adjusted,
                coalesce(c.outstanding, 0) AS outstanding, w.wallet_debt
              FROM users u
              LEFT JOIN academic_department_mappings m ON
            """ + departmentKey("m.department_value") + " = " + departmentKey("u.department") + """

              LEFT JOIN academic_faculties f ON f.faculty_id = m.faculty_id
              LEFT JOIN overdue o ON o.user_id = u.user_id
              LEFT JOIN charges c ON c.user_id = u.user_id
              LEFT JOIN reductions r ON r.user_id = u.user_id
              LEFT JOIN wallets w ON w.user_id = u.user_id
              WHERE u.user_type = 'STUDENT'
            )
            """;

    public StudentObligationReportRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Faculty> faculties() {
        return jdbc.query("SELECT faculty_id, faculty_name FROM academic_faculties ORDER BY faculty_name, faculty_id",
                (rs, row) -> new Faculty(rs.getString(1), rs.getString(2)));
    }

    public ClassificationCoverage classificationCoverage() {
        var groups = jdbc.query("SELECT u.department, m.faculty_id, count(*) AS student_count FROM users u "
                        + "LEFT JOIN academic_department_mappings m ON " + departmentKey("m.department_value") + " = " + departmentKey("u.department")
                        + " WHERE u.user_type = 'STUDENT' GROUP BY u.department, m.faculty_id ORDER BY u.department NULLS FIRST",
                (rs, row) -> new CoverageGroup(rs.getString("department"), rs.getString("faculty_id"), rs.getLong("student_count")));
        long total = 0, classified = 0;
        var unmapped = new java.util.ArrayList<UnmappedDepartment>();
        for (var group : groups) {
            total += group.count();
            if (group.facultyId() != null) classified += group.count();
            else unmapped.add(new UnmappedDepartment(group.department(), group.count()));
        }
        return new ClassificationCoverage(total, classified, total - classified, total == classified, List.copyOf(unmapped));
    }

    private record CoverageGroup(String department, String facultyId, long count) {}

    public long count(Filter filter, LocalDateTime snapshot) {
        return jdbc.queryForObject(BASE + "SELECT count(*) FROM students " + where(filter), params(filter, snapshot), Long.class);
    }

    public List<Student> students(Filter filter, LocalDateTime snapshot, Integer page, int size) {
        String sql = BASE + "SELECT * FROM students " + where(filter)
                + " ORDER BY mssv NULLS LAST, full_name NULLS LAST, user_id";
        var parameters = params(filter, snapshot);
        if (page != null) {
            sql += " LIMIT :limit OFFSET :offset";
            parameters.addValue("limit", size).addValue("offset", (long) page * size);
        }
        return jdbc.query(sql, parameters, (rs, row) -> {
            BigDecimal charged = rs.getBigDecimal("charged"), satisfied = rs.getBigDecimal("satisfied");
            BigDecimal waived = rs.getBigDecimal("waived"), adjusted = rs.getBigDecimal("adjusted");
            BigDecimal outstanding = rs.getBigDecimal("outstanding"), wallet = rs.getBigDecimal("wallet_debt");
            LocalDateTime due = time(rs, "earliest_due");
            return new Student(rs.getObject("user_id", UUID.class), rs.getString("mssv"), rs.getString("full_name"),
                    rs.getString("raw_department"), rs.getString("faculty_id"), rs.getString("faculty_name"),
                    rs.getString("major_name"), rs.getString("mapping_status"), rs.getLong("overdue_count"), due,
                    days(due, snapshot), charged, satisfied.subtract(waived).subtract(adjusted), waived, adjusted,
                    outstanding, wallet, wallet == null ? null : wallet.subtract(outstanding),
                    charged.subtract(satisfied).subtract(outstanding));
        });
    }

    public List<OverdueItem> overdueItems(List<UUID> users, LocalDateTime snapshot) {
        if (users.isEmpty()) return List.of();
        return jdbc.query("SELECT t.user_id, t.ticket_id, t.ticket_type, d.detail_id, d.barcode, d.due_date, "
                        + "(SELECT b.title FROM book_copies bc JOIN books b ON b.book_id = bc.book_id WHERE bc.barcode = d.barcode) AS title "
                        + OVERDUE + " AND t.user_id IN (:users) ORDER BY t.user_id, d.due_date, d.detail_id",
                new MapSqlParameterSource("users", users).addValue("snapshot", snapshot),
                (rs, row) -> new OverdueItem(rs.getObject("user_id", UUID.class), rs.getLong("ticket_id"),
                        rs.getLong("detail_id"), rs.getString("barcode"), rs.getString("title"), rs.getString("ticket_type"),
                        time(rs, "due_date"), days(time(rs, "due_date"), snapshot)));
    }

    public List<LedgerEntry> ledgerEntries(List<UUID> users) {
        if (users.isEmpty()) return List.of();
        return jdbc.query("SELECT *, CASE WHEN amount > 0 AND status IN (" + OPEN + ") "
                        + "THEN greatest(amount - coalesce(paid_amount, 0), 0) ELSE 0 END AS outstanding "
                        + "FROM transactions WHERE user_id IN (:users) AND ((amount > 0 AND type IN (" + CHARGES + ")) "
                        + "OR (amount < 0 AND status = 'COMPLETED' AND type IN ('PAYMENT', 'ADJUSTMENT', 'WAIVER_ADJUSTMENT'))) "
                        + "ORDER BY user_id, created_at NULLS LAST, transaction_id",
                new MapSqlParameterSource("users", users), ledgerMapper());
    }

    private RowMapper<LedgerEntry> ledgerMapper() {
        return (rs, row) -> new LedgerEntry(rs.getObject("user_id", UUID.class), rs.getLong("transaction_id"),
                rs.getObject("ticket_id", Long.class), rs.getObject("loan_detail_id", Long.class),
                rs.getObject("penalty_case_id", Long.class), rs.getString("type"), rs.getString("reason"),
                rs.getString("status"), rs.getBigDecimal("amount"), rs.getBigDecimal("paid_amount"),
                rs.getBigDecimal("outstanding"), rs.getString("payment_method"), rs.getString("receipt_number"), time(rs, "created_at"));
    }

    private String where(Filter filter) {
        String condition = switch (filter.mode()) {
            case OVERDUE -> "overdue_count > 0";
            case DEBT -> "(outstanding > 0 OR coalesce(wallet_debt, 0) > 0)";
            case EITHER -> "(overdue_count > 0 OR outstanding > 0 OR coalesce(wallet_debt, 0) > 0)";
        };
        if (filter.facultyId() != null) condition += " AND faculty_id = :faculty";
        if (!filter.query().isEmpty()) condition += " AND (strpos(lower(coalesce(mssv, '')), lower(:query)) > 0 "
                + "OR strpos(lower(coalesce(full_name, '')), lower(:query)) > 0)";
        return "WHERE " + condition;
    }

    private MapSqlParameterSource params(Filter filter, LocalDateTime snapshot) {
        return new MapSqlParameterSource("snapshot", snapshot).addValue("faculty", filter.facultyId()).addValue("query", filter.query());
    }

    private static LocalDateTime time(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }
    private static long days(LocalDateTime due, LocalDateTime snapshot) {
        return due == null ? 0 : java.time.temporal.ChronoUnit.DAYS.between(due.toLocalDate(), snapshot.toLocalDate());
    }
}
