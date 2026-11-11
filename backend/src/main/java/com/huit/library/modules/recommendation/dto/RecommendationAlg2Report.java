package com.huit.library.modules.recommendation.dto;

import java.time.Instant;
import java.util.List;

public record RecommendationAlg2Report(
        String datasetName,
        Instant generatedAt,
        int topK,
        String candidateRule,
        DatasetWarning warning,
        List<MethodMetrics> metrics,
        List<SampleRecommendation> samples,
        Diagnostics diagnostics) {

    public record DatasetWarning(
            int evaluationUsers, long trainInteractions, long testInteractions, String message) {}

    public record MethodMetrics(
            String method,
            double precisionAtK,
            double recallAtK,
            double hitRateAtK,
            double catalogCoverage,
            double averageLatencyMs,
            double p95LatencyMs,
            int evaluatedUsers) {}

    public record SampleRecommendation(
            String sampleLabel,
            String cohort,
            String method,
            boolean fallback,
            String fallbackReason,
            List<Item> items) {}

    public record Item(int rank, Long bookId, String title, double score) {}

    public record Diagnostics(
            long catalogBooks,
            long trainingInteractions,
            long validationInteractions,
            long testInteractions,
            long trainBooks,
            long newBooks,
            long newBooksWithText,
            boolean trainingUsesOnlyTrainSplit) {}
}
