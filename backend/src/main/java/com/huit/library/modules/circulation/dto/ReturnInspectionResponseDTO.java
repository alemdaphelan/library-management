package com.huit.library.modules.circulation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Immutable receipt snapshot, including balances as of the original operation. */
public record ReturnInspectionResponseDTO(
        UUID operationId, String operationType, LocalDateTime processedAt, boolean replayed,
        List<ItemResult> items, List<TicketResult> tickets, List<ReaderBalance> balances) {

    public ReturnInspectionResponseDTO asReplay() {
        return new ReturnInspectionResponseDTO(operationId, operationType, processedAt, true, items, tickets, balances);
    }

    public record ItemResult(
            Long loanDetailId, Long ticketId, UUID userId, String barcode,
            String condition, String detailStatus, String copyStatus,
            Long penaltyCaseId, String penaltyStatus,
            BigDecimal lateFineAdded, BigDecimal lateFineTotal, BigDecimal penaltyAmount,
            BigDecimal depositApplied, BigDecimal debtAdded, BigDecimal penaltyOutstanding,
            LocalDateTime returnDate, LocalDateTime fineStoppedAt) { }

    public record TicketResult(Long ticketId, String status) { }

    public record ReaderBalance(UUID userId, BigDecimal depositBalance, BigDecimal totalDebt) { }
}
