package com.huit.library.modules.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "library.rag")
public class RagProperties {
    private boolean enabled = true;
    private String apiKey = "";
    private String apiBaseUrl = "https://generativelanguage.googleapis.com/v1beta";
    private String generationModel = "gemini-3.6-flash";
    private String embeddingModel = "gemini-embedding-2";
    private String embeddingProvider = "auto";
    private int embeddingDimension = 768;
    private int topK = 5;
    private double minimumScore = 0.60;
    private int maxInputCharacters = 1_000;
    private int chunkSizeCharacters = 900;
    private int chunkOverlapCharacters = 120;
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 20;
    private int requestsPerMinute = 10;
    private int generationThinkingBudget = 0;
}
