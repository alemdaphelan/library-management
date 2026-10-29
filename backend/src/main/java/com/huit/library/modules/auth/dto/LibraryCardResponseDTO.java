package com.huit.library.modules.auth.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record LibraryCardResponseDTO(
        boolean issued,
        String cardNumber,
        String ownerName,
        String department,
        String cardStatus,
        LocalDateTime issuedAt,
        LocalDateTime expiryDate,
        BigDecimal depositBalance,
        BigDecimal totalDebt,
        LocalDateTime circulationLockedUntil,
        String circulationLockReason) {
}
