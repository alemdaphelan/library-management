package com.huit.library.modules.dashboard.dto;

import com.huit.library.modules.dashboard.dto.CirculationAnalytics.Range;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class FinanceStockAnalytics {
    private FinanceStockAnalytics() {}

    public enum EventKind { CHARGE, PAYMENT, DEPOSIT_APPLICATION, WAIVER, ADJUSTMENT, DEPOSIT_TOPUP }

    public record Money(BigDecimal chargesAccrued, BigDecimal moneyReceived, BigDecimal cashReceived,
            BigDecimal bankReceived, BigDecimal vnpayReceived, BigDecimal otherReceived,
            BigDecimal allocatedPayments, BigDecimal depositApplied, BigDecimal waived,
            BigDecimal adjusted, BigDecimal depositTopups) {}
    public record MoneyBucket(LocalDate start, LocalDate until, Money amounts) {}
    public record Fee(String feeType, BigDecimal chargesAccrued) {}
    public record CurrentDebt(BigDecimal ledgerOutstanding, BigDecimal walletDebt,
            BigDecimal walletMinusLedger, BigDecimal satisfiedIncludingReductions) {}
    public record FinanceCoverage(long chargesWithoutIssueTime, long chargesWithoutDepositEvidence,
            long depositApplicationsWithoutTime, long paymentsWithoutPaidTime, long reductionsWithoutIssueTime,
            long paymentAllocationMismatches, BigDecimal receiptMinusEligibleAllocations,
            long invalidAllocationRows) {}
    public record FinanceReport(LocalDateTime snapshotAt, String timezone, Range range,
            Money totals, List<MoneyBucket> buckets, List<Fee> feeTypes, CurrentDebt currentDebt,
            FinanceCoverage coverage, boolean historicalDebtAvailable) {}
    public record Event(EventKind kind, long transactionId, LocalDateTime at, String feeType,
            BigDecimal amount, String method, String receiptNumber, Long parentTransactionId) {}
    public record EventPage(LocalDateTime snapshotAt, String timezone, Range range, EventKind kind,
            int page, int size, long total, List<Event> items) {}
    public record Allocation(long allocationId, Long debtTransactionId, String feeType,
            BigDecimal amount, boolean eligible) {}
    public record AllocationPage(LocalDateTime snapshotAt, long paymentTransactionId, int page,
            int size, long total, List<Allocation> items) {}

    public record Imports(long completedReceipts, long importedTitles, long firstRecordedImportTitles,
            long importedCopies) {}
    public record ImportBucket(LocalDate start, LocalDate until, Imports counts,
            Long liquidatedCopies, Long destroyedCopies) {}
    public record ImportCoverage(long completedReceiptsWithoutTime, long invalidDetailRows,
            long titlesWithUndatedImport) {}
    public record DisposalAvailability(String status, String reason, Long liquidatedCopies, Long destroyedCopies) {}
    public record StockReport(LocalDateTime snapshotAt, String timezone, Range range,
            Imports totals, List<ImportBucket> buckets, ImportCoverage coverage, DisposalAvailability disposals) {}
    public record Receipt(long receiptId, String receiptCode, LocalDateTime completedAt,
            long importedTitles, long importedCopies, String detailApi) {}
    public record ReceiptPage(LocalDateTime snapshotAt, String timezone, Range range, int page,
            int size, long total, List<Receipt> items) {}
}
