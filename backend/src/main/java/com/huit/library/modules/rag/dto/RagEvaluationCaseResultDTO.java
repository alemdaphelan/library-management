package com.huit.library.modules.rag.dto;

import java.util.List;

public record RagEvaluationCaseResultDTO(
        String id,
        String category,
        String question,
        List<String> expectedSourceKeys,
        List<String> retrievedSourceKeys,
        boolean answerExpected,
        boolean refused,
        boolean retrievalHit,
        double retrievalRecallAtK,
        boolean sourceCorrect,
        boolean answerContentCorrect,
        boolean citationValid,
        boolean grounded,
        boolean fallbackUsed,
        double topRetrievalScore,
        String model,
        long latencyMs,
        String status,
        String answer) {
}
