package com.huit.library.modules.recommendation.experiment;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface Alg2RecommendationModel {

    String method();

    RecommendationResult recommend(UUID userId, int k, Set<Long> excludedBookIds);

    record RecommendationResult(
            UUID userId,
            String method,
            boolean fallback,
            String fallbackReason,
            long latencyNanos,
            List<RecommendationItem> items) {}

    record RecommendationItem(Long bookId, double score, int rank) {}
}
