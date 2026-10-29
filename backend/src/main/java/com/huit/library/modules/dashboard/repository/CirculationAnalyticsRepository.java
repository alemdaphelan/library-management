package com.huit.library.modules.dashboard.repository;
import com.huit.library.modules.dashboard.dto.CirculationAnalytics.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class CirculationAnalyticsRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public CirculationAnalyticsRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    private static final String ACTUAL = "(d.checkout_at IS NOT NULL OR d.return_status IN ('BORROWING','BORROWED','OVERDUE','RETURNED','LOST','DAMAGED','SETTLED'))";
    private static final String CURRENT = "d.return_date IS NULL AND d.physical_return_at IS NULL AND d.return_status IN ('BORROWING','BORROWED','OVERDUE') AND t.status IN ('BORROWING','BORROWED','OVERDUE') AND (d.checkout_at IS NULL OR d.checkout_at<=:snapshot)";
    private static final String OVERDUE = " FROM loan_details d JOIN loan_tickets t ON t.ticket_id=d.ticket_id JOIN users u ON u.user_id=t.user_id WHERE " + CURRENT
            + " AND d.fine_stopped_at IS NULL AND d.due_date<:snapshot AND u.user_type='STUDENT'";
    public MapSqlParameterSource params(Range range, LocalDateTime snapshot) {
        return new MapSqlParameterSource("from", range.from().atStartOfDay()).addValue("until", range.until().atStartOfDay()).addValue("snapshot", snapshot);
    }
    public Counts totals(Range range, LocalDateTime snapshot) {
        return jdbc.queryForObject("""
                SELECT (SELECT count(*) FROM loan_details WHERE checkout_at>=:from AND checkout_at<:until AND checkout_at<=:snapshot) ci,
                  (SELECT count(*) FROM loan_details WHERE physical_return_at>=:from AND physical_return_at<:until AND physical_return_at<=:snapshot) ri,
                  (SELECT count(DISTINCT ticket_id) FROM loan_details WHERE checkout_at>=:from AND checkout_at<:until AND checkout_at<=:snapshot) ct,
                  (SELECT count(DISTINCT ticket_id) FROM loan_details WHERE physical_return_at>=:from AND physical_return_at<:until AND physical_return_at<=:snapshot) rt
                """, params(range,snapshot), (rs,n) -> new Counts(rs.getLong("ci"),rs.getLong("ri"),rs.getLong("ct"),rs.getLong("rt")));
    }
    public List<Bucket> buckets(Range range, LocalDateTime snapshot) {
        String unit = range.period().name().toLowerCase(Locale.ROOT);
        String step = switch(range.period()) { case MONTH -> "1 month"; case QUARTER -> "3 months"; case YEAR -> "1 year"; };
        return jdbc.query("""
                WITH buckets AS (SELECT greatest(s,:from) start_at, least(s+interval '%s',:until) end_at
                  FROM generate_series(date_trunc('%s',cast(:from AS timestamp)),cast(:until AS timestamp)-interval '1 microsecond',interval '%s') s)
                SELECT start_at,end_at,
                  (SELECT count(*) FROM loan_details WHERE checkout_at>=start_at AND checkout_at<end_at AND checkout_at<=:snapshot) ci,
                  (SELECT count(*) FROM loan_details WHERE physical_return_at>=start_at AND physical_return_at<end_at AND physical_return_at<=:snapshot) ri,
                  (SELECT count(DISTINCT ticket_id) FROM loan_details WHERE checkout_at>=start_at AND checkout_at<end_at AND checkout_at<=:snapshot) ct,
                  (SELECT count(DISTINCT ticket_id) FROM loan_details WHERE physical_return_at>=start_at AND physical_return_at<end_at AND physical_return_at<=:snapshot) rt
                FROM buckets ORDER BY start_at
                """.formatted(step,unit,step), params(range,snapshot), (rs,n) -> new Bucket(rs.getTimestamp("start_at").toLocalDateTime().toLocalDate(),rs.getTimestamp("end_at").toLocalDateTime().toLocalDate(),new Counts(rs.getLong("ci"),rs.getLong("ri"),rs.getLong("ct"),rs.getLong("rt"))));
    }
    public Coverage coverage(Range range, LocalDateTime snapshot) {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE checkout_at IS NULL AND %s) missing_checkout,
                  count(*) FILTER (WHERE d.return_status='RETURNED' AND physical_return_at IS NULL) missing_return,
                  count(*) FILTER (WHERE physical_return_at IS NULL AND EXISTS (SELECT 1 FROM penalty_cases p WHERE p.loan_detail_id=d.detail_id AND p.incident_type='DAMAGED')) ambiguous,
                  count(*) FILTER (WHERE checkout_at>=:from AND checkout_at<:until AND checkout_at<=:snapshot AND b.book_id IS NULL) unattributed
                FROM loan_details d LEFT JOIN book_copies c ON c.barcode=d.barcode LEFT JOIN books b ON b.book_id=coalesce(d.circulation_book_id,c.book_id)
                """.formatted(ACTUAL),params(range,snapshot),(rs,n) -> {
            long a=rs.getLong("missing_checkout"),b=rs.getLong("missing_return"),c=rs.getLong("ambiguous"),d=rs.getLong("unattributed");return new Coverage(a,b,c,d,a+b+c+d==0);
        });
    }
    private String rankingsCte() {
        return """
                WITH counts AS (
                  SELECT coalesce(d.circulation_book_id,c.book_id) book_id,
                    count(*) FILTER (WHERE d.checkout_at>=:from AND d.checkout_at<:until AND d.checkout_at<=:snapshot) period_count,
                    count(*) FILTER (WHERE %s AND (d.checkout_at IS NULL OR d.checkout_at<=:snapshot)) lifetime_count,
                    count(*) FILTER (WHERE %s) current_count
                  FROM loan_details d LEFT JOIN book_copies c ON c.barcode=d.barcode LEFT JOIN loan_tickets t ON t.ticket_id=d.ticket_id
                  GROUP BY coalesce(d.circulation_book_id,c.book_id)
                ), copies AS (SELECT book_id,count(*) copy_count FROM book_copies GROUP BY book_id), heads AS (
                  SELECT b.book_id,b.title,b.author,coalesce(c.period_count,0) period_count,coalesce(c.lifetime_count,0) lifetime_count,
                    coalesce(c.current_count,0) current_count,coalesce(p.copy_count,0) copy_count
                  FROM books b LEFT JOIN counts c ON c.book_id=b.book_id LEFT JOIN copies p ON p.book_id=b.book_id
                  WHERE NOT coalesce(b.is_digital,false) OR coalesce(p.copy_count,0)>0
                )
                """.formatted(ACTUAL,CURRENT);
    }
    private String rankingWhere(Ranking mode) { return switch(mode) {
        case MOST_BORROWED, LEAST_BORROWED -> "period_count>0";
        case NEVER_RECORDED -> "lifetime_count=0";
        case NO_RECORDED_IN_PERIOD -> "period_count=0";
        case CURRENTLY_BORROWED -> "current_count>0";
    }; }
    public long rankingCount(Range r, LocalDateTime at, Ranking mode) { return jdbc.queryForObject(rankingsCte()+"SELECT count(*) FROM heads WHERE "+rankingWhere(mode),params(r,at),Long.class); }
    public List<RankedBook> ranking(Range r, LocalDateTime at, Ranking mode, int page, int size) {
        String metric=mode==Ranking.CURRENTLY_BORROWED?"current_count": "period_count";
        String direction=mode==Ranking.LEAST_BORROWED?"ASC":"DESC";
        return jdbc.query(rankingsCte()+"SELECT *,dense_rank() OVER(ORDER BY "+metric+" "+direction+") position FROM heads WHERE "+rankingWhere(mode)
                +" ORDER BY "+metric+" "+direction+",lower(title) NULLS LAST,book_id LIMIT :limit OFFSET :offset",
                params(r,at).addValue("limit",size).addValue("offset",(long)page*size),(rs,n) -> new RankedBook(rs.getLong("book_id"),rs.getString("title"),rs.getString("author"),rs.getLong("period_count"),rs.getLong("lifetime_count"),rs.getLong("current_count"),rs.getLong("copy_count"),rs.getLong("position"),"/book/"+rs.getLong("book_id")));
    }
    public long currentOverdueCount(LocalDateTime snapshot) { return jdbc.queryForObject("SELECT count(*)"+OVERDUE,new MapSqlParameterSource("snapshot",snapshot),Long.class); }
    public List<OverdueItem> overdue(LocalDateTime snapshot,int page,int size) {
        return jdbc.query("SELECT u.user_id,u.mssv,u.full_name,t.ticket_id,d.detail_id,d.barcode,d.due_date,"
                +"coalesce(d.circulation_book_id,(SELECT bc.book_id FROM book_copies bc WHERE bc.barcode=d.barcode)) copy_book_id,"
                +"(SELECT title FROM books WHERE book_id=coalesce(d.circulation_book_id,(SELECT book_id FROM book_copies WHERE barcode=d.barcode))) title"
                +OVERDUE+" ORDER BY d.due_date,d.detail_id LIMIT :limit OFFSET :offset",new MapSqlParameterSource("snapshot",snapshot).addValue("limit",size).addValue("offset",(long)page*size),(rs,n) -> {
            String mssv=rs.getString("mssv"); long ticket=rs.getLong("ticket_id");
            return new OverdueItem(rs.getObject("user_id",UUID.class),mssv,rs.getString("full_name"),ticket,rs.getLong("detail_id"),rs.getString("barcode"),rs.getObject("copy_book_id",Long.class),rs.getString("title"),rs.getTimestamp("due_date").toLocalDateTime(),"/admin/circulation?referenceType=LOAN_TICKET&referenceId="+ticket+"&mode=return","/api/v1/librarian-reports/student-obligations?mode=OVERDUE&q="+java.net.URLEncoder.encode(mssv==null?Objects.toString(rs.getString("full_name"),""):mssv,java.nio.charset.StandardCharsets.UTF_8));
        });
    }
    public Map<LocalDate,Long> dailyBorrowCounts(LocalDate from,LocalDate until) {
        return jdbc.query("SELECT checkout_at::date borrow_day,count(*) n FROM loan_details WHERE checkout_at>=:from AND checkout_at<:until GROUP BY checkout_at::date",
                new MapSqlParameterSource("from",from.atStartOfDay()).addValue("until",until.atStartOfDay()),rs -> {var map=new HashMap<LocalDate,Long>();while(rs.next())map.put(rs.getDate("borrow_day").toLocalDate(),rs.getLong("n"));return map;});
    }
}
