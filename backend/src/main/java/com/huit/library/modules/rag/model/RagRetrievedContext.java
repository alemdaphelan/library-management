package com.huit.library.modules.rag.model;

public record RagRetrievedContext(
        String sourceKey,
        String title,
        String sourceUri,
        String type,
        String content,
        double score) {
}
