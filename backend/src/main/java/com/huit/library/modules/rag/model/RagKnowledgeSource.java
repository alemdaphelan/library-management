package com.huit.library.modules.rag.model;

import java.time.LocalDate;

public record RagKnowledgeSource(
        String sourceKey,
        String title,
        String sourceUri,
        String version,
        LocalDate effectiveDate,
        String content) {
}
