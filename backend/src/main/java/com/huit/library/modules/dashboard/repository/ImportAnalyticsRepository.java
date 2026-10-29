package com.huit.library.modules.dashboard.repository;

import com.huit.library.modules.dashboard.dto.CirculationAnalytics.Range;
import com.huit.library.modules.dashboard.dto.FinanceStockAnalytics.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class ImportAnalyticsRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public ImportAnalyticsRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    // Receipt detail quantity is historical import evidence, independent of copies later lost/deleted.
    private static final String BASE = """
            WITH completed AS (SELECT * FROM goods_receipts WHERE status='COMPLETED'),
            valid_lines AS (SELECT d.*,r.completed_at FROM receipt_details d JOIN completed r ON r.receipt_id=d.receipt_id
              WHERE d.book_id IS NOT NULL AND d.quantity>0),
            first_import AS (SELECT v.book_id,min(v.completed_at) first_at FROM valid_lines v
              WHERE NOT EXISTS(SELECT 1 FROM receipt_details old JOIN completed r ON r.receipt_id=old.receipt_id
                WHERE old.book_id=v.book_id AND r.completed_at IS NULL)
              GROUP BY v.book_id),
            selected_receipts AS (SELECT * FROM completed WHERE completed_at>=:from
              AND completed_at<:until AND completed_at<=:snapshot),
            selected_lines AS (SELECT * FROM valid_lines WHERE completed_at>=:from
              AND completed_at<:until AND completed_at<=:snapshot)
            """;
    private MapSqlParameterSource params(Range r,LocalDateTime at) {
        return new MapSqlParameterSource("from",r.from().atStartOfDay()).addValue("until",r.until().atStartOfDay()).addValue("snapshot",at);
    }
    private Imports counts(ResultSet r) throws SQLException {
        return new Imports(r.getLong("receipts"),r.getLong("titles"),r.getLong("first_titles"),r.getLong("copies"));
    }
    public Imports totals(Range r,LocalDateTime at) {
        return jdbc.queryForObject(BASE+"""
                SELECT (SELECT count(*) FROM selected_receipts) receipts,
                  (SELECT count(DISTINCT book_id) FROM selected_lines) titles,
                  (SELECT count(*) FROM first_import WHERE first_at>=:from AND first_at<:until AND first_at<=:snapshot) first_titles,
                  (SELECT coalesce(sum(quantity::bigint),0) FROM selected_lines) copies
                """,params(r,at),(rs,n)->counts(rs));
    }
    public Map<LocalDate,Imports> buckets(Range r,LocalDateTime at) {
        String unit=r.period().name().toLowerCase(Locale.ROOT);
        String sql=BASE+"""
                , receipt_counts AS (SELECT date_trunc('%s',completed_at)::date bucket,count(*) n FROM selected_receipts GROUP BY bucket),
                line_counts AS (SELECT date_trunc('%s',completed_at)::date bucket,count(DISTINCT book_id) titles,sum(quantity::bigint) copies FROM selected_lines GROUP BY bucket),
                first_counts AS (SELECT date_trunc('%s',first_at)::date bucket,count(*) n FROM first_import
                  WHERE first_at>=:from AND first_at<:until AND first_at<=:snapshot GROUP BY bucket)
                SELECT r.bucket,r.n receipts,coalesce(l.titles,0) titles,coalesce(f.n,0) first_titles,coalesce(l.copies,0) copies
                FROM receipt_counts r LEFT JOIN line_counts l ON l.bucket=r.bucket LEFT JOIN first_counts f ON f.bucket=r.bucket
                """.formatted(unit,unit,unit);
        return jdbc.query(sql,params(r,at),rs->{
            Map<LocalDate,Imports> result=new HashMap<>();
            while(rs.next())result.put(rs.getDate("bucket").toLocalDate(),counts(rs));
            return result;
        });
    }
    public ImportCoverage coverage() {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM goods_receipts WHERE status='COMPLETED' AND completed_at IS NULL) missing,
                  (SELECT count(*) FROM receipt_details d JOIN goods_receipts r ON r.receipt_id=d.receipt_id
                    WHERE r.status='COMPLETED' AND (d.book_id IS NULL OR d.quantity IS NULL OR d.quantity<=0)) invalid,
                  (SELECT count(DISTINCT d.book_id) FROM receipt_details d JOIN goods_receipts r ON r.receipt_id=d.receipt_id
                    WHERE r.status='COMPLETED' AND r.completed_at IS NULL AND d.quantity>0) unknown_titles
                """,new MapSqlParameterSource(),(rs,n)->new ImportCoverage(rs.getLong("missing"),rs.getLong("invalid"),rs.getLong("unknown_titles")));
    }
    public long receiptCount(Range r,LocalDateTime at) {
        return jdbc.queryForObject(BASE+"SELECT count(*) FROM selected_receipts",params(r,at),Long.class);
    }
    public List<Receipt> receipts(Range r,LocalDateTime at,int page,int size) {
        return jdbc.query(BASE+"""
                SELECT r.receipt_id,r.receipt_code,r.completed_at,
                  (SELECT count(DISTINCT book_id) FROM selected_lines d WHERE d.receipt_id=r.receipt_id) titles,
                  (SELECT coalesce(sum(quantity::bigint),0) FROM selected_lines d WHERE d.receipt_id=r.receipt_id) copies
                FROM selected_receipts r ORDER BY r.completed_at,r.receipt_id LIMIT :limit OFFSET :offset
                """,params(r,at).addValue("limit",size).addValue("offset",(long)page*size),(rs,n)->new Receipt(rs.getLong("receipt_id"),
                rs.getString("receipt_code"),rs.getObject("completed_at",LocalDateTime.class),rs.getLong("titles"),rs.getLong("copies"),
                "/inventory/receipts/"+rs.getLong("receipt_id")));
    }
}
