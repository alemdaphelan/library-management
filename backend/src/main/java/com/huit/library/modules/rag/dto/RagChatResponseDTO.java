package com.huit.library.modules.rag.dto;

import java.util.List;
import java.util.UUID;

public record RagChatResponseDTO(
        UUID requestId,
        String answer,
        List<RagSourceDTO> sources,
        List<String> suggestions,
        boolean grounded,
        boolean fallbackUsed,
        String model) {
}
