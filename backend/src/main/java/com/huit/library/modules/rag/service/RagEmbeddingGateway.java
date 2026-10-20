package com.huit.library.modules.rag.service;

import com.huit.library.modules.rag.config.RagProperties;
import org.springframework.stereotype.Service;

@Service
public class RagEmbeddingGateway {
    private final RagProperties properties;
    private final GeminiApiClient geminiApiClient;
    private final LocalHashEmbeddingService localEmbeddingService;

    public RagEmbeddingGateway(
            RagProperties properties,
            GeminiApiClient geminiApiClient,
            LocalHashEmbeddingService localEmbeddingService) {
        this.properties = properties;
        this.geminiApiClient = geminiApiClient;
        this.localEmbeddingService = localEmbeddingService;
    }

    public float[] embedDocument(String text, String title) {
        if (useGemini()) {
            return geminiApiClient.embed(text, "RETRIEVAL_DOCUMENT", title);
        }
        return localEmbeddingService.embed(text);
    }

    public float[] embedQuery(String text) {
        if (useGemini()) {
            return geminiApiClient.embed(text, "RETRIEVAL_QUERY", null);
        }
        return localEmbeddingService.embed(text);
    }

    public String providerKey() {
        if (useGemini()) {
            return "gemini:" + properties.getEmbeddingModel() + ":" + properties.getEmbeddingDimension();
        }
        return localEmbeddingService.providerKey();
    }

    public boolean isRemote() {
        return useGemini();
    }

    private boolean useGemini() {
        String provider = properties.getEmbeddingProvider();
        return !"local".equalsIgnoreCase(provider) && geminiApiClient.isConfigured();
    }
}
