package com.huit.library.modules.reporting.repository;

import com.huit.library.modules.reporting.dto.ProposalSummaryReport.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public class ProposalSummaryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ProposalSummaryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<Source> sources(Filter filter) {
        String sql = """
                SELECT p.*, u.full_name, u.mssv, u.department,
                  coalesce(f.faculty_id, 'UNCLASSIFIED') AS faculty_id,
                  coalesce(f.faculty_name, 'Chưa phân loại') AS faculty_name
                FROM book_proposals p LEFT JOIN users u ON u.user_id = p.user_id
                LEFT JOIN academic_department_mappings m ON u.user_type = 'STUDENT' AND
                """ + StudentObligationReportRepository.departmentKey("m.department_value") + " = "
                + StudentObligationReportRepository.departmentKey("u.department") + """

                LEFT JOIN academic_faculties f ON f.faculty_id = m.faculty_id WHERE 1=1
                """;
        var params = new MapSqlParameterSource();
        if (filter.facultyId() != null) {
            sql += " AND coalesce(f.faculty_id, 'UNCLASSIFIED') = :faculty";
            params.addValue("faculty", filter.facultyId());
        }
        if (filter.from() != null) { sql += " AND p.created_at >= :from"; params.addValue("from", filter.from().atStartOfDay()); }
        if (filter.to() != null) { sql += " AND p.created_at < :until"; params.addValue("until", filter.to().plusDays(1).atStartOfDay()); }
        if (filter.status() != Status.ALL) {
            sql += " AND coalesce(p.status, 'PENDING') = :status"; params.addValue("status", filter.status().name());
        }
        return jdbc.query(sql + " ORDER BY p.created_at NULLS LAST, p.proposal_id", params, (rs, n) -> new Source(
                rs.getLong("proposal_id"), rs.getObject("user_id", UUID.class), rs.getString("full_name"), rs.getString("mssv"),
                rs.getString("department"), rs.getString("faculty_id"), rs.getString("faculty_name"), rs.getString("title"),
                rs.getString("author_name"), rs.getString("publisher"), rs.getString("isbn"), rs.getObject("publication_year", Integer.class),
                rs.getString("edition"), rs.getString("status") == null ? "PENDING" : rs.getString("status"),
                rs.getTimestamp("created_at") == null ? null : rs.getTimestamp("created_at").toLocalDateTime()));
    }
}
