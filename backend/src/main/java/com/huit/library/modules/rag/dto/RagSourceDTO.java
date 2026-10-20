package com.huit.library.modules.rag.dto;

public record RagSourceDTO(
        String sourceKey,
        String title,
        String sourceUri,
        String type,
        String snippet,
        double score) {
}
