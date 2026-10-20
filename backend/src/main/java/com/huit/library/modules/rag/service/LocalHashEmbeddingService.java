package com.huit.library.modules.rag.service;

import com.huit.library.modules.rag.config.RagProperties;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class LocalHashEmbeddingService {
    private final RagProperties properties;

    public LocalHashEmbeddingService(RagProperties properties) {
        this.properties = properties;
    }

    public float[] embed(String text) {
        int dimension = properties.getEmbeddingDimension();
        float[] vector = new float[dimension];
        List<String> tokens = tokenize(text);
        for (int index = 0; index < tokens.size(); index++) {
            add(vector, tokens.get(index), 1.0f);
            if (index + 1 < tokens.size()) {
                add(vector, tokens.get(index) + "_" + tokens.get(index + 1), 1.5f);
            }
        }
        normalize(vector);
        return vector;
    }

    public String providerKey() {
        return "local-hash-v1:" + properties.getEmbeddingDimension();
    }

    private void add(float[] vector, String token, float weight) {
        int hash = token.hashCode();
        int position = Math.floorMod(hash, vector.length);
        float sign = (hash & 1) == 0 ? 1.0f : -1.0f;
        vector[position] += sign * weight;
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = Normalizer.normalize(text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 2) {
                result.add(token);
            }
        }
        return result;
    }

    private void normalize(float[] vector) {
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm == 0) {
            return;
        }
        double divisor = Math.sqrt(norm);
        for (int index = 0; index < vector.length; index++) {
            vector[index] = (float) (vector[index] / divisor);
        }
    }
}
