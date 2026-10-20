package com.huit.library.modules.recommendation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RecommendationEvaluationResponseDTO(
        LocalDateTime generatedAt,
        int k,
        int requestedScenarios,
        int evaluatedScenarios,
        int classifiedCatalogSize,
        String evaluationDataset,
        RealHistoryAuditDTO realHistory,
        List<EvaluationMethodMetricsDTO> methods,
        String conclusion,
        String disclaimer
) {
}
