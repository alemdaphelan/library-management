package com.huit.library.modules.circulation.model;

import java.util.Locale;

public enum LoanTicketType {
    BORROW,
    ONSITE;

    public static LoanTicketType fromDatabaseValue(String value) {
        if (value == null || value.isBlank()) {
            return BORROW;
        }
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "NORMAL" -> BORROW;
            default -> LoanTicketType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        };
    }
}
