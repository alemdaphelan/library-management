package com.huit.library.modules.recommendation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RecommendationResponseDTO(
        String method,
        boolean dataSufficient,
        int candidateCount,
        LocalDateTime generatedAt,
        List<RecommendationItemDTO> items
) {
}
