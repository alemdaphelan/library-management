package com.huit.library.modules.rag.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagPrivacyFilterTest {
    @Test
    void redactsPersonalIdentifiersBeforeSendingQuestionToGemini() {
        RagPrivacyFilter filter = new RagPrivacyFilter();

        String safe = filter.redact("Email sv@example.com, số 0912345678, MSSV 2020123456 bị khóa thẻ");

        assertThat(safe)
                .doesNotContain("sv@example.com", "0912345678", "2020123456")
                .contains("[EMAIL_ĐÃ_ẨN]", "[SĐT_ĐÃ_ẨN]", "[MSSV_ĐÃ_ẨN]");
    }
}
