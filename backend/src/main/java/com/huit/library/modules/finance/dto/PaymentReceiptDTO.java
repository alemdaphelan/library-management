package com.huit.library.modules.finance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PaymentReceiptDTO {
    private Long paymentTransactionId;
    private String receiptNumber;
    private UUID userId;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private UUID confirmedBy;
    private LocalDateTime paidAt;
    private BigDecimal remainingDebt;
    private List<AllocationLine> allocations;

    @Data
    @Builder
    public static class AllocationLine {
        private Long debtTransactionId;
        private String feeType;
        private String reason;
        private BigDecimal amount;
    }
}
