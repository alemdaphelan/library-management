package com.huit.library.modules.recommendation.experiment;

import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Interaction;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Split;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PopularityBaselineModel implements Alg2RecommendationModel {

    public static final String METHOD = "POPULARITY";

    private final List<ScoredBook> ranking;

    public PopularityBaselineModel(List<Alg2CatalogBook> catalog, List<Interaction> interactions) {
        Map<Long, Double> trainScores = new HashMap<>();
        interactions.stream()
                .filter(interaction -> interaction.split() == Split.TRAIN)
                .forEach(
                        interaction ->
                                trainScores.merge(
                                        interaction.bookId(),
                                        interaction.preferenceWeight(),
                                        Double::sum));
        double maximum =
                trainScores.values().stream().mapToDouble(Double::doubleValue).max().orElse(1.0);
        this.ranking =
                catalog.stream()
                        .map(
                                book ->
                                        new ScoredBook(
                                                book.bookId(),
                                                trainScores.getOrDefault(book.bookId(), 0.0)
                                                        / maximum))
                        .sorted(
                                Comparator.comparingDouble(ScoredBook::score)
                                        .reversed()
                                        .thenComparing(ScoredBook::bookId))
                        .toList();
    }

    @Override
    public String method() {
        return METHOD;
    }

    @Override
    public RecommendationResult recommend(UUID userId, int k, Set<Long> excludedBookIds) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be at least 1");
        }
        long startedAt = System.nanoTime();
        Set<Long> excluded = excludedBookIds == null ? Set.of() : excludedBookIds;
        List<RecommendationItem> items = new ArrayList<>(k);
        for (ScoredBook candidate : ranking) {
            if (items.size() >= k) {
                break;
            }
            if (!excluded.contains(candidate.bookId())) {
                items.add(
                        new RecommendationItem(
                                candidate.bookId(), candidate.score(), items.size() + 1));
            }
        }
        return new RecommendationResult(
                userId, method(), false, null, System.nanoTime() - startedAt, List.copyOf(items));
    }

    private record ScoredBook(Long bookId, double score) {}
}
