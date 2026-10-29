package com.huit.library.integration;

import com.huit.library.modules.dashboard.dto.CirculationAnalytics.Period;
import com.huit.library.modules.dashboard.dto.FinanceStockAnalytics.EventKind;
import com.huit.library.modules.dashboard.repository.*;
import com.huit.library.modules.dashboard.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.*;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@Testcontainers
@JdbcTest(properties={"spring.liquibase.enabled=true","spring.liquibase.change-log=classpath:db/changelog/finance-stock-analytics-test-master.yaml"})
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Import({FinanceAnalyticsRepository.class,ImportAnalyticsRepository.class,FinanceStockAnalyticsService.class,
        CirculationAnalyticsService.class,CirculationAnalyticsRepository.class})
class FinanceStockAnalyticsIntegrationTest {
    @Container static PostgreSQLContainer<?> pg=new PostgreSQLContainer<>("postgres:15-alpine");
    @DynamicPropertySource static void props(DynamicPropertyRegistry r){
        r.add("spring.datasource.url",pg::getJdbcUrl);r.add("spring.datasource.username",pg::getUsername);r.add("spring.datasource.password",pg::getPassword);
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired FinanceStockAnalyticsService service;
    @Autowired FinanceAnalyticsRepository finance;
    private final LocalDate from=LocalDate.of(2026,1,1),until=LocalDate.of(2026,4,1);
    private final UUID reader=UUID.randomUUID();
    @BeforeEach void readerExists() {
        jdbc.update("INSERT INTO users(user_id,email,full_name,user_type) VALUES (?,?,?,'STUDENT')",reader,reader+"@b13.test","B13 Reader");
    }

    @Test void twoHundredThousandWithFiftyDepositThenPartialAndFinalPaymentReconciles() {
        long debt=charge("COMPENSATION",200000,50000,"2026-01-03");
        wallet(150000);
        var initial=service.finance(from,until,Period.MONTH);
        assertThat(initial.totals().chargesAccrued()).isEqualByComparingTo("200000");
        assertThat(initial.totals().depositApplied()).isEqualByComparingTo("50000");
        assertThat(initial.totals().moneyReceived()).isZero();
        assertThat(initial.currentDebt().ledgerOutstanding()).isEqualByComparingTo("150000");
        assertThat(initial.currentDebt().walletMinusLedger()).isZero();
        long cash=payment(60000,"CASH","2026-02-01");allocate(cash,debt,60000);
        jdbc.update("UPDATE transactions SET paid_amount=110000,payment_method='CASH',paid_at='2026-02-01' WHERE transaction_id=?",debt);
        jdbc.update("UPDATE wallets SET total_debt=90000 WHERE user_id=?",reader);
        var partial=service.finance(from,until,Period.MONTH);
        assertThat(partial.totals().cashReceived()).isEqualByComparingTo("60000");
        assertThat(partial.totals().allocatedPayments()).isEqualByComparingTo("60000");
        assertThat(partial.totals().depositApplied()).isEqualByComparingTo("50000");
        assertThat(partial.currentDebt().ledgerOutstanding()).isEqualByComparingTo("90000");
        long bank=payment(90000,"BANK_TRANSFER","2026-03-01");allocate(bank,debt,90000);
        jdbc.update("UPDATE transactions SET paid_amount=200000,status='PAID',payment_method='BANK_TRANSFER',paid_at='2026-03-01' WHERE transaction_id=?",debt);
        jdbc.update("UPDATE wallets SET total_debt=0 WHERE user_id=?",reader);
        var finalReport=service.finance(from,until,Period.MONTH);
        assertThat(finalReport.totals().moneyReceived()).isEqualByComparingTo("150000");
        assertThat(finalReport.totals().bankReceived()).isEqualByComparingTo("90000");
        assertThat(finalReport.totals().depositApplied()).isEqualByComparingTo("50000");
        assertThat(finalReport.currentDebt().ledgerOutstanding()).isZero();
        assertThat(finalReport.buckets().get(0).amounts().depositApplied()).isEqualByComparingTo("50000");
        assertThat(finalReport.buckets().get(1).amounts().depositApplied()).isZero();
        assertThat(finalReport.coverage().paymentAllocationMismatches()).isZero();
        assertThat(service.allocations(cash,0,25).items()).hasSize(1);
        assertThat(service.allocations(cash,0,25).items().get(0).eligible()).isTrue();
    }
    @Test void enoughDepositAndTopUpAreSeparateFromNewMoneyReceived() {
        charge("FINE",200000,200000,"2026-01-01");
        transaction("DEPOSIT_TOPUP",-250000,"COMPLETED","2026-01-01","2026-01-01","VNPAY");
        var report=service.finance(from,until,Period.MONTH);
        assertThat(report.totals().depositApplied()).isEqualByComparingTo("200000");
        assertThat(report.totals().depositTopups()).isEqualByComparingTo("250000");
        assertThat(report.totals().moneyReceived()).isZero();
        assertThat(report.currentDebt().ledgerOutstanding()).isZero();
    }
    @Test void allFeeTypesReductionsAndReverseAdjustmentAreNotPayments() {
        for(String type:new String[]{"FINE","REPAIR_FEE","COMPENSATION","TECHNICAL_FEE"})charge(type,50000,0,"2026-01-03");
        transaction("WAIVER_ADJUSTMENT",-30000,"COMPLETED","2026-02-01",null,null);
        transaction("ADJUSTMENT",-20000,"COMPLETED","2026-02-02",null,null);
        transaction("ADJUSTMENT",5000,"COMPLETED","2026-03-01",null,null);
        transaction("ADJUSTMENT",-80000,"PENDING","2026-03-01",null,null);
        var r=service.finance(from,until,Period.QUARTER);
        assertThat(r.feeTypes()).hasSize(4);
        assertThat(r.totals().chargesAccrued()).isEqualByComparingTo("200000");
        assertThat(r.totals().waived()).isEqualByComparingTo("30000");
        assertThat(r.totals().adjusted()).isEqualByComparingTo("15000");
        assertThat(r.totals().moneyReceived()).isZero();
        assertThat(r.historicalDebtAvailable()).isFalse();
    }
    @Test void receiptIsNotMultipliedBySeveralAllocationsAndDuplicateCallbackIsOneReceipt() {
        long a=charge("FINE",50000,0,"2025-12-01"),b=charge("TECHNICAL_FEE",100000,0,"2025-12-01");
        long p=payment(150000,"VNPAY","2026-01-02");allocate(p,a,50000);allocate(p,b,100000);
        jdbc.update("UPDATE transactions SET dedup_key='PAYMENT:B13-SAME' WHERE transaction_id=?",p);
        assertThatThrownBy(()->jdbc.update("INSERT INTO transactions(type,amount,status,dedup_key) VALUES ('PAYMENT',-150000,'COMPLETED','PAYMENT:B13-SAME')"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        // A failed SQL statement aborts the test transaction; the successful aggregation is asserted in a separate test.
    }
    @Test void receiptAndAllocationTotalsRemainIndependentAndInvalidLinesAreVisible() {
        long a=charge("FINE",50000,0,"2025-12-01"),b=charge("TECHNICAL_FEE",100000,0,"2025-12-01");
        long p=payment(150000,"VNPAY","2026-01-02");allocate(p,a,50000);allocate(p,b,100000);
        var r=service.finance(from,until,Period.MONTH);
        assertThat(r.totals().moneyReceived()).isEqualByComparingTo("150000");
        assertThat(r.totals().allocatedPayments()).isEqualByComparingTo("150000");
        assertThat(r.totals().chargesAccrued()).isZero();
        assertThat(finance.lifetimeMoneyReceived()).isEqualByComparingTo("150000");
        allocate(p,a,1); // bad imported allocation must be a discrepancy, never another receipt
        r=service.finance(from,until,Period.MONTH);
        assertThat(r.totals().moneyReceived()).isEqualByComparingTo("150000");
        assertThat(r.coverage().receiptMinusEligibleAllocations()).isEqualByComparingTo("-1");
        long invalid=transaction("DEPOSIT_TOPUP",-100,"COMPLETED","2026-01-01","2026-01-01","VNPAY");
        allocate(p,invalid,100);
        assertThat(service.finance(from,until,Period.MONTH).coverage().invalidAllocationRows()).isEqualTo(1);
    }
    @Test void datesQuarterYearZeroBucketsAndPaymentStatusUseBusinessTimes() {
        transaction("PAYMENT",-100,"COMPLETED","2025-12-31","2026-01-01","CASH");
        transaction("PAYMENT",-200,"COMPLETED","2026-02-01","2026-04-01","BANK_TRANSFER");
        transaction("PAYMENT",-500,"FAILED","2026-01-01","2026-01-01","CASH");
        var r=service.finance(from,until,Period.MONTH);
        assertThat(r.totals().cashReceived()).isEqualByComparingTo("100");
        assertThat(r.buckets()).hasSize(3);
        assertThat(r.buckets().get(1).amounts().moneyReceived()).isZero();
        assertThat(service.finance(LocalDate.of(2025,12,31),LocalDate.of(2026,1,2),Period.YEAR).buckets()).hasSize(2);
        var q=service.finance(LocalDate.of(2026,1,2),LocalDate.of(2026,5,2),Period.QUARTER);
        assertThat(q.buckets()).hasSize(2);
        assertThat(q.buckets().get(0).start()).isEqualTo(LocalDate.of(2026,1,2));
        assertThat(q.buckets().get(1).until()).isEqualTo(LocalDate.of(2026,5,2));
        assertThat(q.totals().bankReceived()).isEqualByComparingTo("200");
    }
    @Test void unknownLegacyDepositOrDatesAreNotGuessedAndWalletIsCurrentOnly() {
        long d=transaction("FINE",200000,"PARTIALLY_PAID","2025-12-01",null,"CASH");
        jdbc.update("UPDATE transactions SET paid_amount=50000 WHERE transaction_id=?",d);
        transaction("PAYMENT",-100,"COMPLETED","2026-01-01",null,"CASH");
        wallet(170000);
        var r=service.finance(from,until,Period.MONTH);
        assertThat(r.totals().depositApplied()).isZero();
        assertThat(r.coverage().chargesWithoutDepositEvidence()).isEqualTo(1);
        assertThat(r.coverage().paymentsWithoutPaidTime()).isEqualTo(1);
        assertThat(r.currentDebt().ledgerOutstanding()).isEqualByComparingTo("150000");
        assertThat(r.currentDebt().walletMinusLedger()).isEqualByComparingTo("20000");
        assertThat(r.historicalDebtAvailable()).isFalse();
    }
    @Test void importsCountReceiptQuantitiesDistinctTitlesAndFirstRecordedImportAcrossBatches() {
        long b=book("Same head"),c=book("New head");
        receipt("COMPLETED","2025-12-01",b,2);
        long jan=receipt("COMPLETED","2026-01-31T23:59:59",b,3);line(jan,b,2);
        receipt("COMPLETED","2026-02-01",b,4);receipt("COMPLETED","2026-02-01",c,5);
        receipt("COMPLETED","2026-04-01",c,100);receipt("PENDING","2026-01-01",c,100);
        var r=service.stock(from,until,Period.MONTH);
        assertThat(r.totals().completedReceipts()).isEqualTo(3);
        assertThat(r.totals().importedCopies()).isEqualTo(14);
        assertThat(r.totals().importedTitles()).isEqualTo(2);
        assertThat(r.totals().firstRecordedImportTitles()).isEqualTo(1);
        assertThat(r.buckets().get(0).counts().importedCopies()).isEqualTo(5);
        assertThat(r.buckets().get(1).counts().importedTitles()).isEqualTo(2);
        assertThat(r.buckets().get(2).counts().importedCopies()).isZero();
        assertThat(service.receipts(from,until,0,1).items()).hasSize(1);
        assertThat(service.receipts(from,until,0,1).total()).isEqualTo(3);
    }
    @Test void oldUndatedCompletionsDoNotInventNewHeadsOrDisposalResults() {
        long b=book("Has unknown history");receipt("COMPLETED",null,b,2);
        long good=receipt("COMPLETED","2026-01-01",b,3);line(good,b,-1);
        jdbc.update("INSERT INTO book_copies(barcode,book_id,status) VALUES ('B13-RETIRED',?,'RETIRED')",b);
        var r=service.stock(from,until,Period.MONTH);
        assertThat(r.totals().importedCopies()).isEqualTo(3);
        assertThat(r.totals().firstRecordedImportTitles()).isZero();
        assertThat(r.coverage().completedReceiptsWithoutTime()).isEqualTo(1);
        assertThat(r.coverage().invalidDetailRows()).isEqualTo(1);
        assertThat(r.coverage().titlesWithUndatedImport()).isEqualTo(1);
        assertThat(r.disposals().status()).isEqualTo("WAITING_ADMIN_WORKFLOW");
        assertThat(r.disposals().liquidatedCopies()).isNull();
        assertThat(r.buckets().get(0).destroyedCopies()).isNull();
        jdbc.update("DELETE FROM book_copies WHERE book_id=?",b);
        assertThat(service.stock(from,until,Period.MONTH).totals().importedCopies()).isEqualTo(3);
    }
    @Test void emptyPagesRangeValidationAndReadOnlyReportsDoNotMutateBalances() {
        assertThat(service.finance(from,until,Period.MONTH).totals().moneyReceived()).isZero();
        assertThat(service.stock(from,until,Period.QUARTER).buckets()).hasSize(1);
        assertThat(service.events(from,until,EventKind.PAYMENT,99,25).items()).isEmpty();
        assertThat(service.receipts(from,until,99,25).items()).isEmpty();
        assertThatThrownBy(()->service.events(from,until,null,-1,25)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->service.finance(until,from,Period.MONTH)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThatThrownBy(()->service.allocations(999999,0,25)).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transactions",Long.class)).isZero();
    }
    private long transaction(String type,int amount,String status,String created,String paid,String method) {
        return jdbc.queryForObject("INSERT INTO transactions(user_id,type,amount,status,created_at,paid_at,payment_method,paid_amount) VALUES (?,?,?,?,cast(? AS timestamp),cast(? AS timestamp),?,0) RETURNING transaction_id",Long.class,reader,type,amount,status,created,paid,method);
    }
    private long charge(String type,int amount,int deposit,String at) {
        long id=transaction(type,amount,deposit==amount?"PAID":deposit>0?"PARTIALLY_PAID":"OUTSTANDING",at,deposit>0?at:null,deposit>0?"SECURITY_DEPOSIT":null);
        jdbc.update("UPDATE transactions SET paid_amount=?,deposit_applied_amount=?,deposit_applied_at=cast(? AS timestamp) WHERE transaction_id=?",deposit,deposit,deposit>0?at:null,id);
        return id;
    }
    private long payment(int amount,String method,String at){return transaction("PAYMENT",-amount,"COMPLETED",at,at,method);}
    private void allocate(long payment,long debt,int amount){jdbc.update("INSERT INTO payment_allocations(payment_transaction_id,debt_transaction_id,amount) VALUES (?,?,?)",payment,debt,amount);}
    private void wallet(int debt){
        jdbc.update("INSERT INTO wallets(user_id,total_debt,deposit_balance) VALUES (?,?,0)",reader,debt);
    }
    private long book(String title){return jdbc.queryForObject("INSERT INTO books(title,created_at) VALUES (?,'2020-01-01') RETURNING book_id",Long.class,title);}
    private long receipt(String status,String completed,long book,int qty){
        long id=jdbc.queryForObject("INSERT INTO goods_receipts(status,completed_at,created_at) VALUES (?,cast(? AS timestamp),'2020-01-01') RETURNING receipt_id",Long.class,status,completed);
        line(id,book,qty);return id;
    }
    private void line(long receipt,long book,int qty){jdbc.update("INSERT INTO receipt_details(receipt_id,book_id,quantity,unit_price) VALUES (?,?,?,1000)",receipt,book,qty);}
}
