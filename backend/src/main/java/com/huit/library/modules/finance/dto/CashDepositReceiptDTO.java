package com.huit.library.modules.finance.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CashDepositReceiptDTO(
        Long transactionId,
        String receiptNumber,
        UUID userId,
        BigDecimal amount,
        String paymentMethod,
        UUID confirmedBy,
        LocalDateTime paidAt,
        BigDecimal depositBalance) {}
