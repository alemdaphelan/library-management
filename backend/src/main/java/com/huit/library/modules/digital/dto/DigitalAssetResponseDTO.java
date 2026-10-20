package com.huit.library.modules.digital.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DigitalAssetResponseDTO(
        Long id,
        Long bookId,
        String bookTitle,
        String name,
        String type,
        String contentType,
        Long size,
        String accessLevel,
        UUID uploadedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
