package com.huit.library.modules.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DashboardStatsDTO(
        long totalCopies,
        long availableCopies,
        long borrowedCurrent,
        long reservedCopies,
        long overdueBooks,
        BigDecimal fineCollected,
        LocalDateTime asOf) {
}
