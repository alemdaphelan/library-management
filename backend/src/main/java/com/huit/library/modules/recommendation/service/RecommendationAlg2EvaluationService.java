package com.huit.library.modules.recommendation.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.recommendation.dto.RecommendationAlg2Report;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Interaction;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Split;
import com.huit.library.modules.recommendation.entity.BookSubjectAssignmentEntity;
import com.huit.library.modules.recommendation.experiment.Alg2CatalogBook;
import com.huit.library.modules.recommendation.experiment.Alg2RecommendationModel;
import com.huit.library.modules.recommendation.experiment.ContentTfIdfBaselineModel;
import com.huit.library.modules.recommendation.experiment.PopularityBaselineModel;
import com.huit.library.modules.recommendation.repository.BookSubjectAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RecommendationAlg2EvaluationService {

    public static final String CANDIDATE_RULE =
            "All catalog books minus the user's TRAIN and VALIDATION books; TEST is used only as relevance ground truth";

    private final RecommendationExperimentDatasetService datasetService;
    private final BookRepository bookRepository;
    private final BookSubjectAssignmentRepository assignmentRepository;

    @Transactional(readOnly = true)
    public RecommendationAlg2Report evaluate(int topK) {
        return evaluate(datasetService.buildDataset(), loadCatalog(), topK);
    }

    @Transactional(readOnly = true)
    public RecommendationAlg2Report evaluate(RecommendationExperimentDataset dataset, int topK) {
        return evaluate(dataset, loadCatalog(), topK);
    }

    public RecommendationAlg2Report evaluate(
            RecommendationExperimentDataset dataset,
            List<Alg2CatalogBook> catalog,
            int topK) {
        if (topK < 1) {
            throw new IllegalArgumentException("topK must be at least 1");
        }
        List<Alg2CatalogBook> orderedCatalog = catalog.stream()
                .sorted(Comparator.comparing(Alg2CatalogBook::bookId))
                .toList();
        PopularityBaselineModel popularity = new PopularityBaselineModel(orderedCatalog, dataset.interactions());
        ContentTfIdfBaselineModel content = new ContentTfIdfBaselineModel(
                orderedCatalog, dataset.interactions(), popularity);
        List<Alg2RecommendationModel> models = List.of(popularity, content);

        Map<UUID, Set<Long>> exclusions = booksByUser(dataset.interactions(), Set.of(Split.TRAIN, Split.VALIDATION));
        Map<UUID, Set<Long>> relevant = booksByUser(dataset.interactions(), Set.of(Split.TEST));
        List<UUID> evaluationUsers = relevant.keySet().stream().sorted().toList();

        List<RecommendationAlg2Report.MethodMetrics> metrics = models.stream()
                .map(model -> evaluateModel(model, evaluationUsers, exclusions, relevant, orderedCatalog.size(), topK))
                .toList();
        List<RecommendationAlg2Report.SampleRecommendation> samples = buildSamples(
                models, dataset, evaluationUsers, exclusions, orderedCatalog, topK);

        long trainInteractions = count(dataset.interactions(), Split.TRAIN);
        long validationInteractions = count(dataset.interactions(), Split.VALIDATION);
        long testInteractions = count(dataset.interactions(), Split.TEST);
        Set<Long> trainBooks = new HashSet<>();
        dataset.interactions().stream()
                .filter(item -> item.split() == Split.TRAIN)
                .map(Interaction::bookId)
                .forEach(trainBooks::add);
        Set<Long> newBooks = new HashSet<>(dataset.newBookIds());
        long newBooksWithText = orderedCatalog.stream()
                .filter(book -> newBooks.contains(book.bookId()))
                .filter(this::hasTextFeatures)
                .count();
        String warningMessage = evaluationUsers.size() < 30
                ? "Very small evaluation cohort; metrics are illustrative and not statistically conclusive."
                : "Evaluation cohort is large enough for comparison, but confidence intervals should still be reported.";

        return new RecommendationAlg2Report(
                dataset.datasetName(),
                dataset.generatedAt(),
                topK,
                CANDIDATE_RULE,
                new RecommendationAlg2Report.DatasetWarning(
                        evaluationUsers.size(), trainInteractions, testInteractions, warningMessage),
                metrics,
                samples,
                new RecommendationAlg2Report.Diagnostics(
                        orderedCatalog.size(), trainInteractions, validationInteractions, testInteractions,
                        trainBooks.size(), newBooks.size(), newBooksWithText, true));
    }

    private List<Alg2CatalogBook> loadCatalog() {
        Map<Long, List<String>> subjects = new HashMap<>();
        for (BookSubjectAssignmentEntity assignment : assignmentRepository.findAllForEvaluation()) {
            subjects.computeIfAbsent(assignment.getBook().getBookId(), ignored -> new ArrayList<>())
                    .add(assignment.getSubject().getName());
        }
        return bookRepository.findAll().stream()
                .map(book -> toCatalogBook(book, subjects.getOrDefault(book.getBookId(), List.of())))
                .toList();
    }

    private Alg2CatalogBook toCatalogBook(BookEntity book, List<String> subjects) {
        return new Alg2CatalogBook(
                book.getBookId(), book.getTitle(), book.getAuthor(), book.getPublisher(),
                book.getCategory() == null ? null : book.getCategory().getName(),
                book.getDescription(), subjects);
    }

    private RecommendationAlg2Report.MethodMetrics evaluateModel(
            Alg2RecommendationModel model,
            List<UUID> users,
            Map<UUID, Set<Long>> exclusions,
            Map<UUID, Set<Long>> relevant,
            int catalogSize,
            int topK) {
        double precision = 0.0;
        double recall = 0.0;
        double hitRate = 0.0;
        Set<Long> coveredBooks = new HashSet<>();
        List<Long> latencies = new ArrayList<>();
        for (UUID userId : users) {
            Alg2RecommendationModel.RecommendationResult result = model.recommend(
                    userId, topK, exclusions.getOrDefault(userId, Set.of()));
            Set<Long> relevantBooks = relevant.getOrDefault(userId, Set.of());
            long hits = result.items().stream()
                    .map(Alg2RecommendationModel.RecommendationItem::bookId)
                    .filter(relevantBooks::contains)
                    .count();
            precision += hits / (double) topK;
            recall += relevantBooks.isEmpty() ? 0.0 : hits / (double) relevantBooks.size();
            hitRate += hits > 0 ? 1.0 : 0.0;
            result.items().forEach(item -> coveredBooks.add(item.bookId()));
            latencies.add(result.latencyNanos());
        }
        int count = users.size();
        latencies.sort(Long::compareTo);
        return new RecommendationAlg2Report.MethodMetrics(
                model.method(), round(count == 0 ? 0.0 : precision / count),
                round(count == 0 ? 0.0 : recall / count),
                round(count == 0 ? 0.0 : hitRate / count),
                round(catalogSize == 0 ? 0.0 : coveredBooks.size() / (double) catalogSize),
                round(nanosToMs(average(latencies))), round(nanosToMs(percentile95(latencies))), count);
    }

    private List<RecommendationAlg2Report.SampleRecommendation> buildSamples(
            List<Alg2RecommendationModel> models,
            RecommendationExperimentDataset dataset,
            List<UUID> evaluationUsers,
            Map<UUID, Set<Long>> exclusions,
            List<Alg2CatalogBook> catalog,
            int topK) {
        Map<Long, String> titles = new HashMap<>();
        catalog.forEach(book -> titles.put(book.bookId(), book.title()));
        List<RecommendationAlg2Report.SampleRecommendation> samples = new ArrayList<>();
        if (!evaluationUsers.isEmpty()) {
            addSample(samples, "EVAL_USER_01", "HAS_TEST_HISTORY", evaluationUsers.get(0), models,
                    exclusions.getOrDefault(evaluationUsers.get(0), Set.of()), titles, topK);
        }
        UUID coldUser = dataset.newUserIds().stream().sorted().findFirst()
                .orElse(UUID.nameUUIDFromBytes("ALG2-COLD-USER".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        addSample(samples, "COLD_USER_01", "NEW", coldUser, models, Set.of(), titles, topK);
        return List.copyOf(samples);
    }

    private void addSample(
            List<RecommendationAlg2Report.SampleRecommendation> output,
            String label,
            String cohort,
            UUID userId,
            List<Alg2RecommendationModel> models,
            Set<Long> excluded,
            Map<Long, String> titles,
            int topK) {
        for (Alg2RecommendationModel model : models) {
            Alg2RecommendationModel.RecommendationResult result = model.recommend(userId, topK, excluded);
            List<RecommendationAlg2Report.Item> items = result.items().stream()
                    .map(item -> new RecommendationAlg2Report.Item(
                            item.rank(), item.bookId(), titles.getOrDefault(item.bookId(), "Unknown"), item.score()))
                    .toList();
            output.add(new RecommendationAlg2Report.SampleRecommendation(
                    label, cohort, model.method(), result.fallback(), result.fallbackReason(), items));
        }
    }

    private Map<UUID, Set<Long>> booksByUser(List<Interaction> interactions, Set<Split> splits) {
        Map<UUID, Set<Long>> result = new LinkedHashMap<>();
        interactions.stream().filter(item -> splits.contains(item.split())).forEach(item ->
                result.computeIfAbsent(item.userId(), ignored -> new HashSet<>()).add(item.bookId()));
        return result;
    }

    private long count(List<Interaction> interactions, Split split) {
        return interactions.stream().filter(item -> item.split() == split).count();
    }

    private boolean hasTextFeatures(Alg2CatalogBook book) {
        return hasText(book.title()) || hasText(book.author()) || hasText(book.publisher())
                || hasText(book.category()) || hasText(book.description()) || !book.subjects().isEmpty();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private double average(List<Long> values) {
        return values.stream().mapToLong(Long::longValue).average().orElse(0.0);
    }

    private double percentile95(List<Long> sortedValues) {
        if (sortedValues.isEmpty()) {
            return 0.0;
        }
        int index = Math.max(0, (int) Math.ceil(sortedValues.size() * 0.95) - 1);
        return sortedValues.get(index);
    }

    private double nanosToMs(double nanos) {
        return nanos / 1_000_000.0;
    }

    private double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
