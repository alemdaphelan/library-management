package com.huit.library.modules.recommendation.dto;

public record EvaluationMethodMetricsDTO(
        String method,
        double precisionAtK,
        double recallAtK,
        double hitRateAtK,
        double catalogCoverage,
        double p50LatencyMs,
        double p95LatencyMs,
        int totalRecommendations
) {
}
