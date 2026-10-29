package com.huit.library.modules.reporting.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record StudentObligationReport(
        LocalDateTime snapshotAt, String timezone, String currency, Filter filter,
        int page, int size, long totalStudents, List<Student> students,
        List<OverdueItem> overdueItems, List<LedgerEntry> ledgerEntries) {

    public enum Mode { OVERDUE, DEBT, EITHER }
    public record Filter(Mode mode, String facultyId, String query) {}
    public record Faculty(String facultyId, String facultyName) {}
    public record UnmappedDepartment(String rawDepartment, long studentCount) {}
    public record ClassificationCoverage(long totalStudents, long classifiedStudents, long unclassifiedStudents,
                                         boolean complete, List<UnmappedDepartment> unmappedDepartments) {}
    public record Student(
            UUID userId, String mssv, String fullName, String rawDepartment,
            String facultyId, String facultyName, String majorName, String mappingStatus,
            long overdueItemCount, LocalDateTime earliestDueDate, long overdueDays,
            BigDecimal chargedAmount, BigDecimal paidOrDepositAmount,
            BigDecimal waivedAmount, BigDecimal adjustedAmount, BigDecimal outstandingAmount,
            BigDecimal walletDebt, BigDecimal walletDifference, BigDecimal reconciliationDifference) {}
    public record OverdueItem(UUID userId, Long ticketId, Long loanDetailId, String barcode,
                              String bookTitle, String ticketType, LocalDateTime dueDate, long overdueDays) {}
    // satisfiedAmount includes payment/deposit/waiver/adjustment; do not label it as cash received.
    public record LedgerEntry(UUID userId, Long transactionId, Long ticketId, Long loanDetailId,
                              Long penaltyCaseId, String type, String reason, String status,
                              BigDecimal amount, BigDecimal satisfiedAmount, BigDecimal outstandingAmount,
                              String paymentMethod, String receiptNumber, LocalDateTime createdAt) {}
}
