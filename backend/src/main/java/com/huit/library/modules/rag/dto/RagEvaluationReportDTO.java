package com.huit.library.modules.rag.dto;

import java.time.Instant;
import java.util.List;

public record RagEvaluationReportDTO(
        Instant generatedAt,
        String datasetVersion,
        String datasetDescription,
        String generationModel,
        String embeddingProvider,
        boolean geminiConfigured,
        int topK,
        int totalCases,
        int answerCases,
        int refusalCases,
        int unexpectedErrors,
        double retrievalHitRateAtK,
        double retrievalRecallAtK,
        double correctSourceRate,
        double groundedAnswerRate,
        double citationRate,
        double correctRefusalRate,
        double fallbackRate,
        long p50LatencyMs,
        long p95LatencyMs,
        List<RagEvaluationCaseResultDTO> cases) {
}
