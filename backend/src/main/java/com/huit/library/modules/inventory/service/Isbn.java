package com.huit.library.modules.inventory.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;

/** ISBN edition identification only; an ISBN contains no shelf/department classification. */
public final class Isbn {
    private Isbn() {}
    public static String canonical(String input) {
        String value = input == null ? "" : input.replaceAll("[\\s\\p{Zs}-]", "").toUpperCase(Locale.ROOT);
        if (value.matches("[0-9]{9}[0-9X]")) {
            int sum = 0;
            for (int i = 0; i < 10; i++) sum += (10 - i) * (value.charAt(i) == 'X' ? 10 : value.charAt(i) - '0');
            if (sum % 11 == 0) return withCheckDigit("978" + value.substring(0, 9));
        }
        if (value.matches("97[89][0-9]{10}") && withCheckDigit(value.substring(0, 12)).equals(value)) return value;
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ISBN-10/ISBN-13 không hợp lệ hoặc sai số kiểm tra");
    }
    public static String legacy(String canonical) {
        if (!canonical.startsWith("978")) return "";
        String value = canonical.substring(3, 12);
        int sum = 0;
        for (int i = 0; i < 9; i++) sum += (10 - i) * (value.charAt(i) - '0');
        int digit = (11 - sum % 11) % 11;
        return value + (digit == 10 ? "X" : String.valueOf(digit));
    }
    private static String withCheckDigit(String first12) {
        int sum = 0;
        for (int i = 0; i < 12; i++) sum += (first12.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        return first12 + ((10 - sum % 10) % 10);
    }
}
