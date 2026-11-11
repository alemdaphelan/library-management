package com.huit.library.modules.recommendation.experiment;

import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Interaction;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Split;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ContentTfIdfBaselineModel implements Alg2RecommendationModel {

    public static final String METHOD = "CONTENT_TFIDF";
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_TERM = Pattern.compile("[^a-z0-9]+");

    private final List<Alg2CatalogBook> catalog;
    private final Map<Long, Alg2CatalogBook> catalogById;
    private final Map<String, Double> inverseDocumentFrequency;
    private final Map<UUID, Map<String, Double>> userProfiles;
    private final PopularityBaselineModel popularityFallback;

    public ContentTfIdfBaselineModel(
            List<Alg2CatalogBook> catalog,
            List<Interaction> interactions,
            PopularityBaselineModel popularityFallback) {
        this.catalog = List.copyOf(catalog);
        this.catalogById = new HashMap<>();
        this.catalog.forEach(book -> catalogById.put(book.bookId(), book));
        this.inverseDocumentFrequency = buildInverseDocumentFrequency(this.catalog);
        this.userProfiles = buildUserProfiles(interactions);
        this.popularityFallback = popularityFallback;
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
        Map<String, Double> profile = userProfiles.get(userId);
        if (profile == null || profile.isEmpty()) {
            RecommendationResult fallback =
                    popularityFallback.recommend(userId, k, excludedBookIds);
            return new RecommendationResult(
                    userId,
                    method(),
                    true,
                    "No TRAIN history; fallback to global popularity",
                    System.nanoTime() - startedAt,
                    fallback.items());
        }

        Set<Long> excluded = excludedBookIds == null ? Set.of() : excludedBookIds;
        Comparator<ScoredBook> worstFirst =
                Comparator.comparingDouble(ScoredBook::score)
                        .thenComparing(ScoredBook::bookId, Comparator.reverseOrder());
        PriorityQueue<ScoredBook> top = new PriorityQueue<>(Math.max(k, 1), worstFirst);
        for (Alg2CatalogBook candidate : catalog) {
            if (excluded.contains(candidate.bookId())) {
                continue;
            }
            double score = cosine(profile, tfIdfVector(candidate));
            ScoredBook scored = new ScoredBook(candidate.bookId(), score);
            if (top.size() < k) {
                top.offer(scored);
            } else if (worstFirst.compare(scored, top.peek()) > 0) {
                top.poll();
                top.offer(scored);
            }
        }

        List<ScoredBook> ordered = new ArrayList<>(top);
        ordered.sort(
                Comparator.comparingDouble(ScoredBook::score)
                        .reversed()
                        .thenComparing(ScoredBook::bookId));
        List<RecommendationItem> items = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            ScoredBook item = ordered.get(index);
            items.add(new RecommendationItem(item.bookId(), round(item.score()), index + 1));
        }
        return new RecommendationResult(
                userId, method(), false, null, System.nanoTime() - startedAt, List.copyOf(items));
    }

    public boolean canScoreBook(Long bookId) {
        Alg2CatalogBook book = catalogById.get(bookId);
        return book != null && !tfIdfVector(book).isEmpty();
    }

    private Map<String, Double> buildInverseDocumentFrequency(List<Alg2CatalogBook> books) {
        Map<String, Integer> documentFrequency = new HashMap<>();
        for (Alg2CatalogBook book : books) {
            for (String term : weightedTerms(book).keySet()) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        Map<String, Double> idf = new HashMap<>();
        double documentCount = books.size();
        documentFrequency.forEach(
                (term, frequency) ->
                        idf.put(term, Math.log((documentCount + 1.0) / (frequency + 1.0)) + 1.0));
        return Map.copyOf(idf);
    }

    private Map<UUID, Map<String, Double>> buildUserProfiles(List<Interaction> interactions) {
        Map<UUID, Map<String, Double>> profiles = new HashMap<>();
        Map<UUID, Double> weights = new HashMap<>();
        interactions.stream()
                .filter(interaction -> interaction.split() == Split.TRAIN)
                .forEach(
                        interaction -> {
                            Alg2CatalogBook book = catalogById.get(interaction.bookId());
                            if (book == null) {
                                return;
                            }
                            Map<String, Double> profile =
                                    profiles.computeIfAbsent(
                                            interaction.userId(), ignored -> new HashMap<>());
                            tfIdfVector(book)
                                    .forEach(
                                            (term, value) ->
                                                    profile.merge(
                                                            term,
                                                            value * interaction.preferenceWeight(),
                                                            Double::sum));
                            weights.merge(
                                    interaction.userId(),
                                    interaction.preferenceWeight(),
                                    Double::sum);
                        });
        profiles.forEach(
                (userId, profile) -> {
                    double totalWeight = weights.getOrDefault(userId, 1.0);
                    profile.replaceAll((term, value) -> value / totalWeight);
                    normalize(profile);
                });
        return Map.copyOf(profiles);
    }

    private Map<String, Double> tfIdfVector(Alg2CatalogBook book) {
        Map<String, Double> vector = weightedTerms(book);
        vector.replaceAll(
                (term, value) -> value * inverseDocumentFrequency.getOrDefault(term, 1.0));
        normalize(vector);
        return vector;
    }

    private Map<String, Double> weightedTerms(Alg2CatalogBook book) {
        Map<String, Double> terms = new HashMap<>();
        addTerms(terms, book.title(), 3.0);
        addTerms(terms, book.author(), 1.0);
        addTerms(terms, book.publisher(), 1.0);
        addTerms(terms, book.category(), 2.0);
        addTerms(terms, book.description(), 1.0);
        book.subjects().forEach(subject -> addTerms(terms, subject, 4.0));
        return terms;
    }

    private void addTerms(Map<String, Double> terms, String text, double weight) {
        if (text == null || text.isBlank()) {
            return;
        }
        String normalized =
                DIACRITICS
                        .matcher(
                                Normalizer.normalize(
                                        text.toLowerCase(Locale.ROOT), Normalizer.Form.NFD))
                        .replaceAll("");
        for (String term : NON_TERM.split(normalized)) {
            if (term.length() >= 2) {
                terms.merge(term, weight, Double::sum);
            }
        }
    }

    private void normalize(Map<String, Double> vector) {
        double norm = Math.sqrt(vector.values().stream().mapToDouble(value -> value * value).sum());
        if (norm > 0.0) {
            vector.replaceAll((term, value) -> value / norm);
        }
    }

    private double cosine(Map<String, Double> left, Map<String, Double> right) {
        Map<String, Double> smaller = left.size() <= right.size() ? left : right;
        Map<String, Double> larger = smaller == left ? right : left;
        return smaller.entrySet().stream()
                .mapToDouble(entry -> entry.getValue() * larger.getOrDefault(entry.getKey(), 0.0))
                .sum();
    }

    private double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    private record ScoredBook(Long bookId, double score) {}
}
