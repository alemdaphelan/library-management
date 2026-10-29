package com.huit.library.modules.rag.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class RagPrivacyFilter {
    private static final Pattern EMAIL = Pattern.compile(
            "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)(?:\\+84|0)(?:\\s*[.-]?\\s*\\d){9,10}(?!\\d)");
    private static final Pattern STUDENT_ID = Pattern.compile(
            "(?i)\\b(?:20\\d{8}|[A-Z]{1,3}\\d{7,10})\\b");

    public String redact(String value) {
        String redacted = EMAIL.matcher(value).replaceAll("[EMAIL_ĐÃ_ẨN]");
        redacted = PHONE.matcher(redacted).replaceAll("[SĐT_ĐÃ_ẨN]");
        return STUDENT_ID.matcher(redacted).replaceAll("[MSSV_ĐÃ_ẨN]");
    }
}
