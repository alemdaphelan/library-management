package com.huit.library.modules.circulation.model;

import java.util.Locale;

public enum LoanDetailStatus {
    PENDING,
    BORROWING,
    OVERDUE,
    RETURNED,
    CANCELLED,
    LOST,
    DAMAGED,
    SETTLED;

    public static LoanDetailStatus fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "COMPLETED" -> RETURNED;
            case "CHECKED_OUT", "ACTIVE" -> BORROWING;
            default -> LoanDetailStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        };
    }
}
