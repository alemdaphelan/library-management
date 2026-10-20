package com.huit.library.modules.rag.service;

import com.huit.library.modules.rag.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class RagTextChunker {
    private final RagProperties properties;

    public RagTextChunker(RagProperties properties) {
        this.properties = properties;
    }

    public List<String> chunk(String markdown) {
        String normalized = markdown == null ? "" : markdown.replace("\r\n", "\n").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        int maximum = Math.max(300, properties.getChunkSizeCharacters());
        int overlap = Math.min(Math.max(0, properties.getChunkOverlapCharacters()), maximum / 3);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : normalized.split("\n\\s*\n")) {
            String clean = paragraph.trim();
            if (clean.isBlank()) {
                continue;
            }
            if (current.length() > 0 && current.length() + clean.length() + 2 > maximum) {
                appendWindowed(chunks, current.toString(), maximum, overlap);
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(clean);
        }
        if (current.length() > 0) {
            appendWindowed(chunks, current.toString(), maximum, overlap);
        }
        return chunks;
    }

    private void appendWindowed(List<String> chunks, String text, int maximum, int overlap) {
        if (text.length() <= maximum) {
            chunks.add(text);
            return;
        }
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maximum, text.length());
            if (end < text.length()) {
                int boundary = text.lastIndexOf(' ', end);
                if (boundary > start + maximum / 2) {
                    end = boundary;
                }
            }
            chunks.add(text.substring(start, end).trim());
            if (end >= text.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
    }
}
