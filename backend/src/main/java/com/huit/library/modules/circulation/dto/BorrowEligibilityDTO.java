package com.huit.library.modules.circulation.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class BorrowEligibilityDTO {
    private int maximumItems;
    private long openItems;
    private long remainingSlots;
    private BigDecimal securityDeposit;
    private BigDecimal currentBorrowedValue;
    private BigDecimal requestedBookValue;
    private BigDecimal maximumBorrowingValue;
    private BigDecimal remainingBorrowingValue;
    private boolean priceKnown;
    private boolean monetaryEligible;
    private boolean cardEligible;
    private boolean eligible;
    private String message;
}
