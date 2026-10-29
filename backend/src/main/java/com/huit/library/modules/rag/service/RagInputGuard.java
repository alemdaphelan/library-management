package com.huit.library.modules.rag.service;

import com.huit.library.modules.rag.config.RagProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

@Component
public class RagInputGuard {
    private static final List<String> INJECTION_MARKERS = List.of(
            "ignore previous instructions",
            "ignore all instructions",
            "reveal system prompt",
            "show system prompt",
            "bỏ qua hướng dẫn trước",
            "bỏ qua mọi chỉ dẫn",
            "hiển thị system prompt",
            "tiết lộ system prompt");

    private final RagProperties properties;

    public RagInputGuard(RagProperties properties) {
        this.properties = properties;
    }

    public String validate(String raw) {
        String question = raw == null ? "" : raw.trim().replace("\u0000", "");
        if (question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu hỏi không được để trống");
        }
        if (question.length() > properties.getMaxInputCharacters()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Câu hỏi vượt quá giới hạn cho phép");
        }
        String lower = question.toLowerCase(Locale.ROOT);
        if (INJECTION_MARKERS.stream().anyMatch(lower::contains)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Câu hỏi chứa chỉ dẫn không phù hợp với trợ lý thư viện");
        }
        return question;
    }
}
