package com.huit.library.modules.rag.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RagChatRequestDTO(
        @NotBlank(message = "Câu hỏi không được để trống")
        @Size(max = 1000, message = "Câu hỏi không được vượt quá 1000 ký tự")
        String message) {
}
