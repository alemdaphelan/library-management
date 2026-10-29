package com.huit.library.modules.rag.service;

import com.huit.library.modules.rag.config.RagProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalHashEmbeddingServiceTest {
    @Test
    void producesStableNormalizedEmbeddingsWithConfiguredDimension() {
        RagProperties properties = new RagProperties();
        properties.setEmbeddingDimension(768);
        LocalHashEmbeddingService service = new LocalHashEmbeddingService(properties);

        float[] first = service.embed("Gia hạn sách thư viện");
        float[] second = service.embed("Gia hạn sách thư viện");

        assertThat(first).hasSize(768).containsExactly(second);
        double norm = 0;
        for (float value : first) {
            norm += value * value;
        }
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
    }
}
