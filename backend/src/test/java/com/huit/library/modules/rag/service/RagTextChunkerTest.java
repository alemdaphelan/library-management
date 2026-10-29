package com.huit.library.modules.rag.service;

import com.huit.library.modules.rag.config.RagProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagTextChunkerTest {
    @Test
    void keepsShortPolicyParagraphsAsGroundedChunks() {
        RagProperties properties = new RagProperties();
        properties.setChunkSizeCharacters(300);
        properties.setChunkOverlapCharacters(30);
        RagTextChunker chunker = new RagTextChunker(properties);

        var chunks = chunker.chunk("# Chính sách\n\nGia hạn tối đa một lần.\n\nQuá hạn không được gia hạn.");

        assertThat(chunks).isNotEmpty();
        assertThat(String.join(" ", chunks)).contains("Gia hạn tối đa một lần", "Quá hạn không được gia hạn");
        assertThat(chunks).allMatch(chunk -> chunk.length() <= 300);
    }
}
