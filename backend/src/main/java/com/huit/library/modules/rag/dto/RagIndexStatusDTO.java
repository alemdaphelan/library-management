package com.huit.library.modules.rag.dto;

public record RagIndexStatusDTO(
        long documents,
        long chunks,
        String embeddingProvider,
        boolean geminiConfigured) {
}
