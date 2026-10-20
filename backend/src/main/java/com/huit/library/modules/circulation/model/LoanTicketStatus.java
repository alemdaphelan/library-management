package com.huit.library.modules.circulation.model;

import java.util.Locale;

public enum LoanTicketStatus {
    PENDING,
    RESERVED,
    BORROWING,
    OVERDUE,
    RETURNED,
    CANCELLED,
    REJECTED;

    public static LoanTicketStatus fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            return PENDING;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "COMPLETED" -> RETURNED;
            case "APPROVED" -> RESERVED;
            case "CHECKED_OUT", "ACTIVE" -> BORROWING;
            default -> LoanTicketStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        };
    }
}
