package com.huit.library.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.huit.library.modules.dashboard.controller.*;
import com.huit.library.modules.dashboard.dto.CirculationAnalytics.*;
import com.huit.library.modules.dashboard.dto.CirculationAnalytics.Period;
import com.huit.library.modules.dashboard.dto.FinanceStockAnalytics.Money;
import com.huit.library.modules.dashboard.repository.*;
import com.huit.library.modules.dashboard.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.*;
import org.springframework.context.annotation.Import;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@JdbcTest(properties={"spring.liquibase.enabled=true","spring.liquibase.change-log=classpath:db/changelog/finance-stock-analytics-test-master.yaml"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({CirculationAnalyticsRepository.class,CirculationAnalyticsService.class,
        FinanceAnalyticsRepository.class,ImportAnalyticsRepository.class,FinanceStockAnalyticsService.class})
class AnalyticsCalendarIntegrationTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:15-alpine");
    @DynamicPropertySource static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired CirculationAnalyticsService circulation;
    @Autowired FinanceStockAnalyticsService finance;
    @Autowired CirculationAnalyticsRepository circulationRepo;
    @Autowired FinanceAnalyticsRepository financeRepo;
    @Autowired ImportAnalyticsRepository importsRepo;

    @ParameterizedTest
    @CsvSource({
        "DAY,2026-01-01,2026-01-01,2026-01-02,24,HOUR",
        "WEEK,2026-01-04,2025-12-29,2026-01-05,7,DAY",
        "WEEK,2026-01-05,2026-01-05,2026-01-12,7,DAY",
        "MONTH,2026-02-17,2026-02-01,2026-03-01,28,DAY",
        "MONTH,2024-02-29,2024-02-01,2024-03-01,29,DAY",
        "MONTH,2026-04-15,2026-04-01,2026-05-01,30,DAY",
        "MONTH,2026-01-31,2026-01-01,2026-02-01,31,DAY",
        "YEAR,2024-08-01,2024-01-01,2025-01-01,12,MONTH"
    })
    void allReportsShareWholeCalendarAndZeroFilledContiguousBuckets(Period period,LocalDate date,
            LocalDate from,LocalDate until,int count,BucketUnit unit) {
        var c=circulation.report(date,null,null,period);
        var f=finance.finance(date,null,null,period);
        var s=finance.stock(date,null,null,period);
        assertThat(c.timezone()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(c.range()).isEqualTo(new Range(from,until,period));
        assertThat(c.range().bucketUnit()).isEqualTo(unit);
        assertThat(f.range()).isEqualTo(c.range());assertThat(s.range()).isEqualTo(c.range());
        assertThat(c.buckets()).hasSize(count);assertThat(f.buckets()).hasSize(count);assertThat(s.buckets()).hasSize(count);
        var cursor=from.atStartOfDay();
        for(int i=0;i<count;i++) {
            var b=c.buckets().get(i);
            assertThat(b.start()).isEqualTo(cursor);assertThat(b.until()).isAfter(b.start());
            assertThat(f.buckets().get(i).start()).isEqualTo(b.start());assertThat(f.buckets().get(i).until()).isEqualTo(b.until());
            assertThat(s.buckets().get(i).start()).isEqualTo(b.start());assertThat(s.buckets().get(i).until()).isEqualTo(b.until());
            assertThat(b.counts()).isEqualTo(new Counts(0,0,0,0));assertThat(f.buckets().get(i).amounts().chargesAccrued()).isZero();
            assertThat(s.buckets().get(i).counts().importedCopies()).isZero();
            assertThat(s.buckets().get(i).liquidatedCopies()).isNull();cursor=b.until();
        }
        assertThat(cursor).isEqualTo(until.atStartOfDay());
    }

    @Test void exactHourlyBoundariesAndAllMoneyFieldsReconcileWithoutDoubleCounting() throws Exception {
        var date=LocalDate.of(2026,1,1);
        // The database session deliberately differs from the business zone. Stored timestamps are local, not UTC.
        jdbc.execute("SET LOCAL TIME ZONE 'America/New_York'");
        long book=jdbc.queryForObject("INSERT INTO books(title) VALUES ('F07') RETURNING book_id",Long.class);
        long ticket=jdbc.queryForObject("INSERT INTO loan_tickets(status) VALUES ('RETURNED') RETURNING ticket_id",Long.class);
        var times=List.of("2025-12-31T23:59:59.999999","2026-01-01T00:00:00","2026-01-01T00:59:59.999999",
                "2026-01-01T01:00:00","2026-01-01T23:59:59.999999","2026-01-02T00:00:00");
        for(var time:times) {
            jdbc.update("INSERT INTO loan_details(ticket_id,return_status,checkout_at,physical_return_at) VALUES (?,'RETURNED',cast(? AS timestamp),cast(? AS timestamp))",ticket,time,time);
            long receipt=jdbc.queryForObject("INSERT INTO goods_receipts(status,completed_at) VALUES ('COMPLETED',cast(? AS timestamp)) RETURNING receipt_id",Long.class,time);
            jdbc.update("INSERT INTO receipt_details(receipt_id,book_id,quantity) VALUES (?,?,2)",receipt,book);
            for(var type:List.of("FINE","PAYMENT","DEPOSIT_TOPUP","WAIVER_ADJUSTMENT","ADJUSTMENT")) {
                int amount=type.equals("FINE")?100:type.equals("ADJUSTMENT")?5:-10;
                jdbc.update("INSERT INTO transactions(type,amount,status,created_at,paid_at,payment_method,deposit_applied_amount,deposit_applied_at) VALUES (?,?,'COMPLETED',cast(? AS timestamp),cast(? AS timestamp),'CASH',?,cast(? AS timestamp))",
                        type,amount,time,time,type.equals("FINE")?20:0,time);
            }
        }
        // Allocation checks remain separate; a completed receipt is counted once even with multiple lines.
        long payment=jdbc.queryForObject("SELECT min(transaction_id) FROM transactions WHERE type='PAYMENT' AND paid_at='2026-01-01'",Long.class);
        long debt=jdbc.queryForObject("SELECT min(transaction_id) FROM transactions WHERE type='FINE'",Long.class);
        UUID reader=UUID.randomUUID();jdbc.update("INSERT INTO users(user_id,user_type) VALUES (?,'STUDENT')",reader);
        jdbc.update("UPDATE transactions SET user_id=?",reader);
        jdbc.update("INSERT INTO payment_allocations(payment_transaction_id,debt_transaction_id,amount) VALUES (?,?,3),(?,?,7)",payment,debt,payment,debt);
        var c=circulation.report(date,null,null,Period.DAY);var f=finance.finance(date,null,null,Period.DAY);var s=finance.stock(date,null,null,Period.DAY);
        assertThat(c.buckets()).hasSize(24);
        assertThat(c.buckets().get(0).counts().checkoutItems()).isEqualTo(2);
        assertThat(c.buckets().get(1).counts().checkoutItems()).isEqualTo(1);
        assertThat(c.buckets().get(23).counts().checkoutItems()).isEqualTo(1);
        assertThat(c.totals().checkoutItems()).isEqualTo(4);assertThat(c.totals().physicalReturnItems()).isEqualTo(4);
        assertThat(c.buckets().stream().mapToLong(b->b.counts().checkoutItems()).sum()).isEqualTo(c.totals().checkoutItems());
        assertThat(c.buckets().stream().mapToLong(b->b.counts().physicalReturnItems()).sum()).isEqualTo(c.totals().physicalReturnItems());
        assertThat(c.totals().checkoutTickets()).isEqualTo(1); // Distinct tickets deliberately aren't additive.
        assertThat(c.buckets().stream().mapToLong(b->b.counts().checkoutTickets()).sum()).isEqualTo(3);
        assertThat(f.totals().chargesAccrued()).isEqualByComparingTo("400");
        assertThat(f.totals().moneyReceived()).isEqualByComparingTo("40");assertThat(f.totals().allocatedPayments()).isEqualByComparingTo("10");
        assertThat(f.totals().depositApplied()).isEqualByComparingTo("80");assertThat(f.totals().adjusted()).isEqualByComparingTo("-20");
        for(var component:Money.class.getRecordComponents()) {
            BigDecimal sum=BigDecimal.ZERO;
            for(var bucket:f.buckets())sum=sum.add((BigDecimal)component.getAccessor().invoke(bucket.amounts()));
            assertThat(sum).as(component.getName()).isEqualByComparingTo((BigDecimal)component.getAccessor().invoke(f.totals()));
        }
        assertThat(s.totals().importedCopies()).isEqualTo(8);assertThat(s.totals().completedReceipts()).isEqualTo(4);
        assertThat(s.buckets().stream().mapToLong(b->b.counts().importedCopies()).sum()).isEqualTo(8);
        assertThat(s.buckets().stream().mapToLong(b->b.counts().completedReceipts()).sum()).isEqualTo(4);
        assertThat(s.totals().importedTitles()).isEqualTo(1);
        assertThat(s.buckets().stream().mapToLong(b->b.counts().importedTitles()).sum()).isEqualTo(3); // Distinct titles also overlap.
        var next=circulation.report(date.plusDays(1),null,null,Period.DAY);assertThat(next.totals().checkoutItems()).isEqualTo(1);
        assertThat(finance.finance(date.plusDays(1),null,null,Period.DAY).totals().chargesAccrued()).isEqualByComparingTo("100");
        assertThat(finance.stock(date.plusDays(1),null,null,Period.DAY).totals().importedCopies()).isEqualTo(2);
    }

    @Test void snapshotExcludesFutureEventsButKeepsAllZeroFilledIntervals() {
        var range=new Range(LocalDate.of(2026,1,1),LocalDate.of(2026,1,2),Period.DAY);
        var snapshot=LocalDateTime.of(2026,1,1,10,0);
        for(String at:List.of("2026-01-01T10:00:00","2026-01-01T10:00:00.000001")) {
            jdbc.update("INSERT INTO loan_details(checkout_at) VALUES (cast(? AS timestamp))",at);
            jdbc.update("INSERT INTO transactions(type,amount,status,paid_at) VALUES ('PAYMENT',-10,'COMPLETED',cast(? AS timestamp))",at);
            jdbc.update("INSERT INTO goods_receipts(status,completed_at) VALUES ('COMPLETED',cast(? AS timestamp))",at);
        }
        var buckets=circulationRepo.buckets(range,snapshot);assertThat(buckets).hasSize(24);
        assertThat(buckets.stream().mapToLong(b->b.counts().checkoutItems()).sum()).isEqualTo(1);
        assertThat(circulationRepo.totals(range,snapshot).checkoutItems()).isEqualTo(1);
        assertThat(financeRepo.buckets(range,snapshot).get(snapshot).moneyReceived()).isEqualByComparingTo("10");
        assertThat(financeRepo.totals(range,snapshot).moneyReceived()).isEqualByComparingTo("10");
        assertThat(importsRepo.buckets(range,snapshot).get(snapshot).completedReceipts()).isEqualTo(1);
        assertThat(importsRepo.totals(range,snapshot).completedReceipts()).isEqualTo(1);
    }

    @Test void endpointsBindReferenceDateAndSerializeHourlyBoundsAndRejectAmbiguousOrOversizedRequests() throws Exception {
        var mapper=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var mvc=MockMvcBuilders.standaloneSetup(new CirculationAnalyticsController(circulation),new FinanceStockAnalyticsController(finance))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        for(var path:List.of("circulation","finance","stock-movements")) {
            mvc.perform(get("/dashboard/analytics/"+path).param("date","2026-01-01").param("period","DAY"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
                    .andExpect(jsonPath("$.range.bucketUnit").value("HOUR")).andExpect(jsonPath("$.buckets.length()").value(24))
                    .andExpect(jsonPath("$.buckets[0].start").value("2026-01-01T00:00:00"))
                    .andExpect(jsonPath("$.buckets[23].until").value("2026-01-02T00:00:00"));
            mvc.perform(get("/dashboard/analytics/"+path).param("date","2026-01-01").param("from","2026-01-01"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/dashboard/analytics/"+path).param("from","2026-01-01").param("until","2026-03-01").param("period","DAY"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/dashboard/analytics/"+path).param("date","2026-02-30")).andExpect(status().isBadRequest());
        }
        var start=LocalDate.of(2026,1,1);
        assertThat(AnalyticsBuckets.bounds(circulation.range(start,start.plusDays(41),Period.DAY))).hasSize(984);
        assertThatThrownBy(()->circulation.range(start,start.plusDays(42),Period.DAY)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("1000");
        assertThatThrownBy(()->circulation.resolveRange(LocalDate.of(9999,12,31),null,null,Period.YEAR)).isInstanceOf(ResponseStatusException.class);
    }

    @Test void chartBucketLimitDoesNotRestrictPagedDrillDownsOrRankings() {
        var from=LocalDate.of(2000,1,1);var until=LocalDate.of(2026,1,1);
        assertThatThrownBy(()->circulation.report(from,until,Period.MONTH)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("1000");
        assertThat(circulation.ranking(from,until,Ranking.MOST_BORROWED,0,10).books()).isEmpty();
        assertThat(finance.events(from,until,null,0,10).items()).isEmpty();
        assertThat(finance.receipts(from,until,0,10).items()).isEmpty();
    }
}
