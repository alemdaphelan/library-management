package com.huit.library.modules.rag.service;

import com.huit.library.modules.rag.config.RagProperties;
import com.huit.library.modules.rag.model.RagRetrievedContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RagRetrievalService {
    private final RagProperties properties;
    private final RagEmbeddingGateway embeddingGateway;
    private final RagKnowledgeStore knowledgeStore;
    private final RagCatalogRetriever catalogRetriever;

    public RagRetrievalService(
            RagProperties properties,
            RagEmbeddingGateway embeddingGateway,
            RagKnowledgeStore knowledgeStore,
            RagCatalogRetriever catalogRetriever) {
        this.properties = properties;
        this.embeddingGateway = embeddingGateway;
        this.knowledgeStore = knowledgeStore;
        this.catalogRetriever = catalogRetriever;
    }

    public List<RagRetrievedContext> retrieve(String question) {
        int topK = Math.max(1, Math.min(properties.getTopK(), 10));
        List<RagRetrievedContext> combined = new ArrayList<>(knowledgeStore.retrieve(
                embeddingGateway.embedQuery(question),
                embeddingGateway.providerKey(),
                topK,
                properties.getMinimumScore()));
        combined.addAll(catalogRetriever.retrieve(question, Math.min(3, topK)));
        return combined.stream()
                .sorted(Comparator.comparingDouble(RagRetrievedContext::score).reversed())
                .limit(topK)
                .toList();
    }
}
