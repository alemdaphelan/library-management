package com.huit.library.modules.finance.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OnlinePaymentSessionDTO {
    private String reference;
    private String provider;
    private String purpose;
    private BigDecimal amount;
    private String status;
    private String paymentUrl;
    private Long paymentTransactionId;
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
}
