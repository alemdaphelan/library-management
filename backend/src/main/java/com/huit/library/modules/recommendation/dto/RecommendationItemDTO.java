package com.huit.library.modules.recommendation.dto;

public record RecommendationItemDTO(
        Long bookId,
        String title,
        String author,
        String publisher,
        String categoryName,
        String imageUrl,
        double score,
        double contentScore,
        double departmentScore,
        double popularityScore,
        long borrowCount,
        String reason
) {
}
