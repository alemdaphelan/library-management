package com.huit.library.modules.recommendation.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.circulation.repository.BookPopularityProjection;
import com.huit.library.modules.circulation.repository.EvaluationInteractionProjection;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.recommendation.config.RecommendationProperties;
import com.huit.library.modules.recommendation.dto.EvaluationMethodMetricsDTO;
import com.huit.library.modules.recommendation.dto.RealHistoryAuditDTO;
import com.huit.library.modules.recommendation.dto.RecommendationEvaluationResponseDTO;
import com.huit.library.modules.recommendation.entity.BookSubjectAssignmentEntity;
import com.huit.library.modules.recommendation.entity.DepartmentSubjectAffinityEntity;
import com.huit.library.modules.recommendation.repository.BookSubjectAssignmentRepository;
import com.huit.library.modules.recommendation.repository.DepartmentSubjectAffinityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class RecommendationEvaluationService {
    static final String PROXY_DATASET = "SYNTHETIC_TOPIC_PROXY_FROM_PRODUCTION_TAXONOMY";
    private static final int PROFILE_BOOKS = 3;
    private static final int MIN_RELEVANT_BOOKS = 5;
    private static final int MAX_CANDIDATES = 1_500;
    private static final int MIN_REAL_USERS_FOR_PUBLICATION = 30;
    private static final int MIN_REAL_INTERACTIONS_FOR_PUBLICATION = 200;

    private final BookSubjectAssignmentRepository assignmentRepository;
    private final DepartmentSubjectAffinityRepository affinityRepository;
    private final LoanDetailRepository loanDetailRepository;
    private final TfIdfSimilarityEngine similarityEngine;
    private final RecommendationProperties properties;

    public RecommendationEvaluationService(
            BookSubjectAssignmentRepository assignmentRepository,
            DepartmentSubjectAffinityRepository affinityRepository,
            LoanDetailRepository loanDetailRepository,
            TfIdfSimilarityEngine similarityEngine,
            RecommendationProperties properties) {
        this.assignmentRepository = assignmentRepository;
        this.affinityRepository = affinityRepository;
        this.loanDetailRepository = loanDetailRepository;
        this.similarityEngine = similarityEngine;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public RecommendationEvaluationResponseDTO evaluate(int requestedK, int requestedScenarios) {
        int k = clamp(requestedK, 1, 50);
        int scenarioLimit = clamp(requestedScenarios, 1, 100);
        RealHistoryAuditDTO realHistory = auditRealHistory();

        List<BookSubjectAssignmentEntity> assignments = assignmentRepository.findAllForEvaluation();
        List<DepartmentSubjectAffinityEntity> affinities = affinityRepository.findAllActiveForEvaluation();

        LinkedHashMap<Long, BookEntity> catalogById = assignments.stream()
                .map(BookSubjectAssignmentEntity::getBook)
                .filter(this::hasUsableTitle)
                .collect(Collectors.toMap(
                        BookEntity::getBookId,
                        book -> book,
                        (first, ignored) -> first,
                        LinkedHashMap::new));
        List<BookEntity> catalog = catalogById.values().stream()
                .sorted(Comparator.comparing(BookEntity::getBookId))
                .toList();

        Map<Long, List<BookSubjectAssignmentEntity>> assignmentsByBook = assignments.stream()
                .collect(Collectors.groupingBy(assignment -> assignment.getBook().getBookId()));
        Map<Long, List<String>> subjectTerms = assignments.stream()
                .collect(Collectors.groupingBy(
                        assignment -> assignment.getBook().getBookId(),
                        Collectors.mapping(
                                assignment -> assignment.getSubject().getCode() + " "
                                        + assignment.getSubject().getName(),
                                Collectors.toList())));
        Map<Long, List<BookEntity>> booksBySubject = assignments.stream()
                .filter(assignment -> assignment.getConfidence().doubleValue() >= 0.70)
                .collect(Collectors.groupingBy(
                        assignment -> assignment.getSubject().getSubjectId(),
                        Collectors.collectingAndThen(
                                Collectors.mapping(BookSubjectAssignmentEntity::getBook, Collectors.toList()),
                                books -> books.stream()
                                        .filter(this::hasUsableTitle)
                                        .collect(Collectors.toMap(
                                                BookEntity::getBookId,
                                                book -> book,
                                                (first, ignored) -> first))
                                        .values().stream()
                                        .sorted(Comparator.comparing(BookEntity::getBookId))
                                        .toList())));
        Map<String, List<DepartmentSubjectAffinityEntity>> affinitiesByDepartment = affinities.stream()
                .collect(Collectors.groupingBy(
                        DepartmentSubjectAffinityEntity::getDepartmentKey,
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<Long, Double> popularityScores = loadPopularityScores();
        double[] weights = normalizedWeights();

        Map<String, MethodAccumulator> accumulators = new LinkedHashMap<>();
        accumulators.put("POPULARITY_BASELINE", new MethodAccumulator("POPULARITY_BASELINE"));
        accumulators.put("CONTENT_TFIDF", new MethodAccumulator("CONTENT_TFIDF"));
        accumulators.put("PROPOSED_HYBRID", new MethodAccumulator("PROPOSED_HYBRID"));

        int evaluatedScenarios = 0;
        for (Map.Entry<String, List<DepartmentSubjectAffinityEntity>> entry : affinitiesByDepartment.entrySet()) {
            if (evaluatedScenarios >= scenarioLimit || entry.getValue().isEmpty()) {
                break;
            }
            DepartmentSubjectAffinityEntity primaryAffinity = entry.getValue().get(0);
            Long targetSubjectId = primaryAffinity.getSubject().getSubjectId();
            List<BookEntity> targetBooks = booksBySubject.getOrDefault(targetSubjectId, List.of());
            if (targetBooks.size() < PROFILE_BOOKS + MIN_RELEVANT_BOOKS) {
                continue;
            }

            List<BookEntity> profileBooks = selectProfileBooks(targetBooks, entry.getKey());
            Set<Long> profileIds = profileBooks.stream()
                    .map(BookEntity::getBookId)
                    .collect(Collectors.toSet());
            List<BookEntity> candidates = buildCandidates(targetBooks, catalog, profileIds);
            Set<Long> relevantIds = targetBooks.stream()
                    .map(BookEntity::getBookId)
                    .filter(id -> !profileIds.contains(id))
                    .filter(id -> candidates.stream().anyMatch(candidate -> candidate.getBookId().equals(id)))
                    .collect(Collectors.toSet());
            if (relevantIds.size() < MIN_RELEVANT_BOOKS) {
                continue;
            }

            Map<Long, Double> departmentWeights = entry.getValue().stream()
                    .collect(Collectors.toMap(
                            affinity -> affinity.getSubject().getSubjectId(),
                            affinity -> affinity.getWeight().doubleValue(),
                            Math::max));
            List<TfIdfSimilarityEngine.WeightedBook> weightedProfile = profileBooks.stream()
                    .map(book -> new TfIdfSimilarityEngine.WeightedBook(book, 1.0))
                    .toList();

            if (evaluatedScenarios == 0) {
                // Warm each implementation once so JIT/class loading does not distort p50/p95.
                rankByPopularity(candidates, popularityScores, k);
                rankByContent(weightedProfile, candidates, subjectTerms, k);
                rankByHybrid(
                        weightedProfile,
                        candidates,
                        subjectTerms,
                        assignmentsByBook,
                        departmentWeights,
                        popularityScores,
                        weights,
                        k);
            }

            evaluateMethod(
                    accumulators.get("POPULARITY_BASELINE"),
                    relevantIds,
                    k,
                    () -> rankByPopularity(candidates, popularityScores, k));
            evaluateMethod(
                    accumulators.get("CONTENT_TFIDF"),
                    relevantIds,
                    k,
                    () -> rankByContent(weightedProfile, candidates, subjectTerms, k));
            evaluateMethod(
                    accumulators.get("PROPOSED_HYBRID"),
                    relevantIds,
                    k,
                    () -> rankByHybrid(
                            weightedProfile,
                            candidates,
                            subjectTerms,
                            assignmentsByBook,
                            departmentWeights,
                            popularityScores,
                            weights,
                            k));
            evaluatedScenarios++;
        }

        int scenarioCount = evaluatedScenarios;
        List<EvaluationMethodMetricsDTO> metrics = accumulators.values().stream()
                .map(accumulator -> accumulator.toDto(scenarioCount, catalog.size()))
                .toList();
        String conclusion = buildConclusion(metrics, evaluatedScenarios);
        return new RecommendationEvaluationResponseDTO(
                LocalDateTime.now(),
                k,
                scenarioLimit,
                evaluatedScenarios,
                catalog.size(),
                PROXY_DATASET,
                realHistory,
                metrics,
                conclusion,
                "Các metric trong methods là proxy theo độ phù hợp chủ đề trên taxonomy production, "
                        + "không phải hành vi mượn thật. Không được trình bày chúng như kết quả người dùng thực tế.");
    }

    private RealHistoryAuditDTO auditRealHistory() {
        List<EvaluationInteractionProjection> interactions = loanDetailRepository.findEvaluationInteractions();
        Map<java.util.UUID, List<EvaluationInteractionProjection>> byUser = interactions.stream()
                .collect(Collectors.groupingBy(EvaluationInteractionProjection::getUserId));
        int eligibleUsers = 0;
        int trainingInteractions = 0;
        int testInteractions = 0;
        for (List<EvaluationInteractionProjection> history : byUser.values()) {
            if (history.size() < 5) {
                continue;
            }
            int testSize = Math.max(1, (int) Math.ceil(history.size() * 0.20));
            int trainingSize = history.size() - testSize;
            if (trainingSize >= 2) {
                eligibleUsers++;
                trainingInteractions += trainingSize;
                testInteractions += testSize;
            }
        }
        boolean sufficient = eligibleUsers >= MIN_REAL_USERS_FOR_PUBLICATION
                && interactions.size() >= MIN_REAL_INTERACTIONS_FOR_PUBLICATION;
        String note = sufficient
                ? "Đủ ngưỡng tối thiểu nội bộ để chạy holdout hành vi; vẫn cần mô tả thiên lệch dữ liệu."
                : "Chưa đủ tối thiểu 30 người dùng hợp lệ và 200 tương tác; không công bố Precision/Recall như dữ liệu thật.";
        return new RealHistoryAuditDTO(
                "PRODUCTION_LOAN_HISTORY",
                interactions.size(),
                byUser.size(),
                eligibleUsers,
                trainingInteractions,
                testInteractions,
                sufficient,
                "Theo từng người dùng: 80% tương tác đầu làm train, 20% cuối theo thời gian làm test; cần ít nhất 5 tương tác/người.",
                note);
    }

    private Map<Long, Double> loadPopularityScores() {
        LocalDateTime asOf = LocalDateTime.now();
        List<BookPopularityProjection> popularity = loanDetailRepository.findPopularityScores(
                asOf.minusDays(3_650),
                asOf,
                365.0,
                PageRequest.of(0, 5_000));
        double max = popularity.stream()
                .mapToDouble(BookPopularityProjection::getWeightedScore)
                .filter(Double::isFinite)
                .max()
                .orElse(0.0);
        if (max <= 0) {
            return Map.of();
        }
        return popularity.stream().collect(Collectors.toMap(
                BookPopularityProjection::getBookId,
                item -> Math.log1p(Math.max(item.getWeightedScore(), 0.0)) / Math.log1p(max),
                Math::max));
    }

    private List<BookEntity> selectProfileBooks(List<BookEntity> books, String departmentKey) {
        int start = Math.floorMod(departmentKey.hashCode(), books.size());
        List<BookEntity> profile = new ArrayList<>();
        for (int offset = 0; offset < books.size() && profile.size() < PROFILE_BOOKS; offset++) {
            BookEntity book = books.get((start + offset) % books.size());
            if (profile.stream().noneMatch(existing -> existing.getBookId().equals(book.getBookId()))) {
                profile.add(book);
            }
        }
        return profile;
    }

    private List<BookEntity> buildCandidates(
            List<BookEntity> targetBooks,
            List<BookEntity> catalog,
            Set<Long> profileIds) {
        LinkedHashMap<Long, BookEntity> candidates = new LinkedHashMap<>();
        targetBooks.stream()
                .filter(book -> !profileIds.contains(book.getBookId()))
                .forEach(book -> candidates.putIfAbsent(book.getBookId(), book));
        catalog.stream()
                .filter(book -> !profileIds.contains(book.getBookId()))
                .forEach(book -> candidates.putIfAbsent(book.getBookId(), book));
        return candidates.values().stream().limit(MAX_CANDIDATES).toList();
    }

    private List<Long> rankByPopularity(
            List<BookEntity> candidates,
            Map<Long, Double> popularity,
            int k) {
        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble((BookEntity book) -> popularity.getOrDefault(book.getBookId(), 0.0))
                        .reversed()
                        .thenComparing(BookEntity::getBookId))
                .limit(k)
                .map(BookEntity::getBookId)
                .toList();
    }

    private List<Long> rankByContent(
            List<TfIdfSimilarityEngine.WeightedBook> profile,
            List<BookEntity> candidates,
            Map<Long, List<String>> subjectTerms,
            int k) {
        Map<Long, Double> scores = similarityEngine.scoreProfile(profile, candidates, subjectTerms);
        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble((BookEntity book) -> scores.getOrDefault(book.getBookId(), 0.0))
                        .reversed()
                        .thenComparing(BookEntity::getBookId))
                .limit(k)
                .map(BookEntity::getBookId)
                .toList();
    }

    private List<Long> rankByHybrid(
            List<TfIdfSimilarityEngine.WeightedBook> profile,
            List<BookEntity> candidates,
            Map<Long, List<String>> subjectTerms,
            Map<Long, List<BookSubjectAssignmentEntity>> assignmentsByBook,
            Map<Long, Double> departmentWeights,
            Map<Long, Double> popularity,
            double[] weights,
            int k) {
        Map<Long, Double> content = similarityEngine.scoreProfile(profile, candidates, subjectTerms);
        Map<Long, Double> hybrid = new HashMap<>();
        for (BookEntity candidate : candidates) {
            double department = assignmentsByBook.getOrDefault(candidate.getBookId(), List.of()).stream()
                    .mapToDouble(assignment -> departmentWeights.getOrDefault(
                                    assignment.getSubject().getSubjectId(), 0.0)
                            * assignment.getConfidence().doubleValue())
                    .max()
                    .orElse(0.0);
            double score = weights[0] * content.getOrDefault(candidate.getBookId(), 0.0)
                    + weights[1] * department
                    + weights[2] * popularity.getOrDefault(candidate.getBookId(), 0.0);
            hybrid.put(candidate.getBookId(), score);
        }
        return candidates.stream()
                .sorted(Comparator
                        .comparingDouble((BookEntity book) -> hybrid.getOrDefault(book.getBookId(), 0.0))
                        .reversed()
                        .thenComparing(BookEntity::getBookId))
                .limit(k)
                .map(BookEntity::getBookId)
                .toList();
    }

    private void evaluateMethod(
            MethodAccumulator accumulator,
            Set<Long> relevantIds,
            int k,
            Supplier<List<Long>> ranking) {
        long startedAt = System.nanoTime();
        List<Long> recommendations = ranking.get();
        double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0;
        long hits = recommendations.stream().filter(relevantIds::contains).count();
        accumulator.precisionSum += recommendations.isEmpty() ? 0.0 : hits / (double) recommendations.size();
        accumulator.recallSum += hits / (double) relevantIds.size();
        accumulator.hitRateSum += hits > 0 ? 1.0 : 0.0;
        accumulator.recommendedBookIds.addAll(recommendations);
        accumulator.totalRecommendations += recommendations.size();
        accumulator.latenciesMs.add(elapsedMs);
    }

    private double[] normalizedWeights() {
        double content = Math.max(0.0, properties.getContentWeight());
        double department = Math.max(0.0, properties.getDepartmentWeight());
        double popularity = Math.max(0.0, properties.getPopularityWeight());
        double total = content + department + popularity;
        return total == 0
                ? new double[]{0.50, 0.35, 0.15}
                : new double[]{content / total, department / total, popularity / total};
    }

    private String buildConclusion(List<EvaluationMethodMetricsDTO> methods, int scenarios) {
        if (scenarios == 0) {
            return "Không có đủ nhóm chủ đề để chạy proxy evaluation.";
        }
        EvaluationMethodMetricsDTO hybrid = methods.stream()
                .filter(method -> method.method().equals("PROPOSED_HYBRID"))
                .findFirst()
                .orElseThrow();
        EvaluationMethodMetricsDTO popularity = methods.stream()
                .filter(method -> method.method().equals("POPULARITY_BASELINE"))
                .findFirst()
                .orElseThrow();
        return hybrid.precisionAtK() >= popularity.precisionAtK()
                ? "Trong proxy theo chủ đề, Hybrid đạt Precision@K không thấp hơn Popularity; cần xác nhận lại bằng lịch sử thật khi đủ mẫu."
                : "Proxy chưa chứng minh Hybrid vượt Popularity; cần xem lại trọng số và dữ liệu trước khi kết luận.";
    }

    private boolean hasUsableTitle(BookEntity book) {
        return book != null && book.getBookId() != null
                && book.getTitle() != null && !book.getTitle().isBlank();
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private static double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private static final class MethodAccumulator {
        private final String method;
        private double precisionSum;
        private double recallSum;
        private double hitRateSum;
        private int totalRecommendations;
        private final Set<Long> recommendedBookIds = new HashSet<>();
        private final List<Double> latenciesMs = new ArrayList<>();

        private MethodAccumulator(String method) {
            this.method = method;
        }

        private EvaluationMethodMetricsDTO toDto(int scenarios, int catalogSize) {
            return new EvaluationMethodMetricsDTO(
                    method,
                    scenarios == 0 ? 0.0 : round(precisionSum / scenarios, 6),
                    scenarios == 0 ? 0.0 : round(recallSum / scenarios, 6),
                    scenarios == 0 ? 0.0 : round(hitRateSum / scenarios, 6),
                    catalogSize == 0 ? 0.0 : round(recommendedBookIds.size() / (double) catalogSize, 6),
                    round(percentile(latenciesMs, 0.50), 3),
                    round(percentile(latenciesMs, 0.95), 3),
                    totalRecommendations);
        }

        private static double percentile(List<Double> values, double percentile) {
            if (values.isEmpty()) {
                return 0.0;
            }
            List<Double> sorted = values.stream().sorted().toList();
            int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
            return sorted.get(index);
        }
    }
}
