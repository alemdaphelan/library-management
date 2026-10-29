package com.huit.library.modules.dashboard.repository;

import com.huit.library.modules.dashboard.dto.CirculationAnalytics.Range;
import com.huit.library.modules.dashboard.dto.FinanceStockAnalytics.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Repository
public class FinanceAnalyticsRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public FinanceAnalyticsRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public static final String FEES = "'FINE','REPAIR_FEE','COMPENSATION','TECHNICAL_FEE'";
    private static final String OPEN = "'OUTSTANDING','PARTIALLY_PAID','PARTIALLY_ADJUSTED','PARTIALLY_WAIVED'";
    // Aggregate allocations before joining a receipt: one receipt must never be multiplied by its lines.
    private static final String BASE = """
            WITH eligible_allocations AS (
              SELECT a.payment_transaction_id, sum(a.amount) allocated
              FROM payment_allocations a JOIN transactions d ON d.transaction_id=a.debt_transaction_id
              JOIN transactions p ON p.transaction_id=a.payment_transaction_id
              WHERE d.type IN (%s) AND d.amount>0 AND a.amount>0 AND d.user_id=p.user_id
                AND p.type='PAYMENT' AND p.status='COMPLETED' AND p.amount<0
              GROUP BY a.payment_transaction_id
            ), events AS (
              SELECT 'CHARGE' kind,transaction_id,created_at at,type fee_type,amount,
                payment_method method,receipt_number,parent_transaction_id,0::numeric allocated
              FROM transactions WHERE type IN (%s) AND amount>0
              UNION ALL
              SELECT 'PAYMENT',p.transaction_id,p.paid_at,p.type,-p.amount,p.payment_method,
                p.receipt_number,p.parent_transaction_id,coalesce(a.allocated,0)
              FROM transactions p LEFT JOIN eligible_allocations a ON a.payment_transaction_id=p.transaction_id
              WHERE p.type='PAYMENT' AND p.status='COMPLETED' AND p.amount<0
              UNION ALL
              SELECT 'DEPOSIT_APPLICATION',transaction_id,deposit_applied_at,type,deposit_applied_amount,
                'SECURITY_DEPOSIT',NULL,parent_transaction_id,0 FROM transactions
              WHERE type IN (%s) AND amount>0 AND deposit_applied_amount>0
              UNION ALL
              SELECT CASE WHEN type='WAIVER_ADJUSTMENT' THEN 'WAIVER' ELSE 'ADJUSTMENT' END,
                transaction_id,created_at,type,-amount,NULL,NULL,parent_transaction_id,0 FROM transactions
              WHERE type IN ('WAIVER_ADJUSTMENT','ADJUSTMENT') AND status='COMPLETED'
              UNION ALL
              SELECT 'DEPOSIT_TOPUP',transaction_id,paid_at,type,-amount,payment_method,
                receipt_number,parent_transaction_id,0 FROM transactions
              WHERE type='DEPOSIT_TOPUP' AND status='COMPLETED' AND amount<0
            )
            """.formatted(FEES, FEES, FEES);
    private static final String IN_RANGE = " at>=:from AND at<:until AND at<=:snapshot ";
    private static final String AMOUNTS = """
            coalesce(sum(amount) FILTER(WHERE kind='CHARGE'),0) charges,
            coalesce(sum(amount) FILTER(WHERE kind='PAYMENT'),0) received,
            coalesce(sum(amount) FILTER(WHERE kind='PAYMENT' AND method='CASH'),0) cash,
            coalesce(sum(amount) FILTER(WHERE kind='PAYMENT' AND method='BANK_TRANSFER'),0) bank,
            coalesce(sum(amount) FILTER(WHERE kind='PAYMENT' AND method='VNPAY'),0) vnpay,
            coalesce(sum(amount) FILTER(WHERE kind='PAYMENT' AND coalesce(method,'') NOT IN ('CASH','BANK_TRANSFER','VNPAY')),0) other,
            coalesce(sum(allocated) FILTER(WHERE kind='PAYMENT'),0) allocated,
            coalesce(sum(amount) FILTER(WHERE kind='DEPOSIT_APPLICATION'),0) deposit,
            coalesce(sum(amount) FILTER(WHERE kind='WAIVER'),0) waived,
            coalesce(sum(amount) FILTER(WHERE kind='ADJUSTMENT'),0) adjusted,
            coalesce(sum(amount) FILTER(WHERE kind='DEPOSIT_TOPUP'),0) topups
            """;

    private MapSqlParameterSource params(Range r, LocalDateTime at) {
        return new MapSqlParameterSource("from", r.from().atStartOfDay())
                .addValue("until", r.until().atStartOfDay()).addValue("snapshot", at);
    }
    private Money money(ResultSet r) throws SQLException {
        return new Money(r.getBigDecimal("charges"),r.getBigDecimal("received"),r.getBigDecimal("cash"),
                r.getBigDecimal("bank"),r.getBigDecimal("vnpay"),r.getBigDecimal("other"),r.getBigDecimal("allocated"),
                r.getBigDecimal("deposit"),r.getBigDecimal("waived"),r.getBigDecimal("adjusted"),r.getBigDecimal("topups"));
    }
    public Money totals(Range r, LocalDateTime at) {
        return jdbc.queryForObject(BASE+"SELECT "+AMOUNTS+" FROM events WHERE "+IN_RANGE,params(r,at),(rs,n)->money(rs));
    }
    public Map<LocalDate,Money> buckets(Range r, LocalDateTime at) {
        String unit=r.period().name().toLowerCase(Locale.ROOT);
        return jdbc.query(BASE+"SELECT date_trunc('"+unit+"',at)::date bucket,"+AMOUNTS
                +" FROM events WHERE "+IN_RANGE+" GROUP BY bucket",params(r,at),rs->{
            Map<LocalDate,Money> result=new HashMap<>();
            while(rs.next()) result.put(rs.getDate("bucket").toLocalDate(),money(rs));
            return result;
        });
    }
    public List<Fee> fees(Range r, LocalDateTime at) {
        return jdbc.query(BASE+"SELECT fee_type,sum(amount) charged FROM events WHERE kind='CHARGE' AND "+IN_RANGE
                +" GROUP BY fee_type ORDER BY fee_type",params(r,at),(rs,n)->new Fee(rs.getString(1),rs.getBigDecimal(2)));
    }
    public CurrentDebt currentDebt() {
        return jdbc.queryForObject("""
                SELECT coalesce(sum(greatest(amount-coalesce(paid_amount,0),0)) FILTER(WHERE status IN (%s)),0) outstanding,
                  coalesce(sum(coalesce(paid_amount,0)),0) satisfied,
                  (SELECT coalesce(sum(total_debt),0) FROM wallets) wallet
                FROM transactions WHERE type IN (%s) AND amount>0
                """.formatted(OPEN,FEES),new MapSqlParameterSource(),(r,n)->new CurrentDebt(r.getBigDecimal("outstanding"),
                r.getBigDecimal("wallet"),r.getBigDecimal("wallet").subtract(r.getBigDecimal("outstanding")),r.getBigDecimal("satisfied")));
    }
    public FinanceCoverage coverage() {
        return jdbc.queryForObject(BASE+"""
                SELECT (SELECT count(*) FROM events WHERE kind='CHARGE' AND at IS NULL) missing_issue,
                  (SELECT count(*) FROM transactions WHERE type IN (%s) AND amount>0 AND deposit_applied_amount IS NULL) missing_deposit,
                  (SELECT count(*) FROM events WHERE kind='DEPOSIT_APPLICATION' AND at IS NULL) missing_deposit_at,
                  (SELECT count(*) FROM events WHERE kind IN ('PAYMENT','DEPOSIT_TOPUP') AND at IS NULL) missing_paid,
                  (SELECT count(*) FROM events WHERE kind IN ('WAIVER','ADJUSTMENT') AND at IS NULL) missing_reduction,
                  (SELECT count(*) FROM events WHERE kind='PAYMENT' AND amount<>allocated) mismatches,
                  (SELECT coalesce(sum(amount-allocated),0) FROM events WHERE kind='PAYMENT') difference,
                  (SELECT count(*) FROM payment_allocations a
                    LEFT JOIN transactions p ON p.transaction_id=a.payment_transaction_id
                    LEFT JOIN transactions d ON d.transaction_id=a.debt_transaction_id
                    WHERE NOT coalesce(p.type='PAYMENT' AND p.status='COMPLETED' AND p.amount<0
                      AND d.type IN (%s) AND d.amount>0 AND a.amount>0 AND p.user_id=d.user_id,false)) invalid
                """.formatted(FEES,FEES),new MapSqlParameterSource(),(r,n)->new FinanceCoverage(r.getLong("missing_issue"),
                r.getLong("missing_deposit"),r.getLong("missing_deposit_at"),r.getLong("missing_paid"),r.getLong("missing_reduction"),
                r.getLong("mismatches"),r.getBigDecimal("difference"),r.getLong("invalid")));
    }
    public long eventCount(Range r,LocalDateTime at,EventKind kind) {
        return jdbc.queryForObject(BASE+"SELECT count(*) FROM events WHERE "+IN_RANGE+(kind==null?"":" AND kind=:kind"),
                params(r,at).addValue("kind",kind==null?null:kind.name()),Long.class);
    }
    public List<Event> events(Range r,LocalDateTime at,EventKind kind,int page,int size) {
        return jdbc.query(BASE+"SELECT * FROM events WHERE "+IN_RANGE+(kind==null?"":" AND kind=:kind")
                +" ORDER BY at,transaction_id,kind LIMIT :limit OFFSET :offset",
                params(r,at).addValue("kind",kind==null?null:kind.name()).addValue("limit",size).addValue("offset",(long)page*size),
                (rs,n)->new Event(EventKind.valueOf(rs.getString("kind")),rs.getLong("transaction_id"),
                        rs.getObject("at",LocalDateTime.class),rs.getString("fee_type"),rs.getBigDecimal("amount"),
                        rs.getString("method"),rs.getString("receipt_number"),rs.getObject("parent_transaction_id",Long.class)));
    }
    public boolean paymentExists(long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM transactions WHERE transaction_id=:id AND type='PAYMENT')",new MapSqlParameterSource("id",id),Boolean.class));
    }
    public long allocationCount(long id) {
        return jdbc.queryForObject("SELECT count(*) FROM payment_allocations WHERE payment_transaction_id=:id",new MapSqlParameterSource("id",id),Long.class);
    }
    public List<Allocation> allocations(long id,int page,int size) {
        return jdbc.query("""
                SELECT a.*,d.type fee_type,coalesce(d.type IN (%s) AND d.amount>0 AND a.amount>0 AND p.user_id=d.user_id
                  AND p.status='COMPLETED' AND p.amount<0,false) eligible
                FROM payment_allocations a JOIN transactions p ON p.transaction_id=a.payment_transaction_id
                LEFT JOIN transactions d ON d.transaction_id=a.debt_transaction_id
                WHERE a.payment_transaction_id=:id ORDER BY a.allocation_id LIMIT :limit OFFSET :offset
                """.formatted(FEES),new MapSqlParameterSource("id",id).addValue("limit",size).addValue("offset",(long)page*size),
                (r,n)->new Allocation(r.getLong("allocation_id"),r.getObject("debt_transaction_id",Long.class),r.getString("fee_type"),r.getBigDecimal("amount"),r.getBoolean("eligible")));
    }
    public BigDecimal lifetimeMoneyReceived() {
        return jdbc.queryForObject("SELECT coalesce(sum(-amount),0) FROM transactions WHERE type='PAYMENT' AND status='COMPLETED' AND amount<0 AND paid_at<=:snapshot",
                new MapSqlParameterSource("snapshot",LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"))),BigDecimal.class);
    }
}
