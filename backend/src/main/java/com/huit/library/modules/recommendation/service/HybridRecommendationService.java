package com.huit.library.modules.recommendation.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.circulation.repository.BookPopularityProjection;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.UserBookInteractionProjection;
import com.huit.library.modules.recommendation.config.RecommendationProperties;
import com.huit.library.modules.recommendation.dto.RecommendationItemDTO;
import com.huit.library.modules.recommendation.dto.RecommendationResponseDTO;
import com.huit.library.modules.recommendation.entity.BookSubjectAssignmentEntity;
import com.huit.library.modules.recommendation.entity.DepartmentSubjectAffinityEntity;
import com.huit.library.modules.recommendation.repository.BookSubjectAssignmentRepository;
import com.huit.library.modules.recommendation.repository.DepartmentSubjectAffinityRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HybridRecommendationService {
    private final BookRepository bookRepository;
    private final BookCopyRepository bookCopyRepository;
    private final LoanDetailRepository loanDetailRepository;
    private final UserRepository userRepository;
    private final BookSubjectAssignmentRepository subjectAssignmentRepository;
    private final DepartmentSubjectAffinityRepository departmentAffinityRepository;
    private final TfIdfSimilarityEngine similarityEngine;
    private final RecommendationCacheService cacheService;
    private final RecommendationProperties properties;

    public HybridRecommendationService(
            BookRepository bookRepository,
            BookCopyRepository bookCopyRepository,
            LoanDetailRepository loanDetailRepository,
            UserRepository userRepository,
            BookSubjectAssignmentRepository subjectAssignmentRepository,
            DepartmentSubjectAffinityRepository departmentAffinityRepository,
            TfIdfSimilarityEngine similarityEngine,
            RecommendationCacheService cacheService,
            RecommendationProperties properties) {
        this.bookRepository = bookRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.loanDetailRepository = loanDetailRepository;
        this.userRepository = userRepository;
        this.subjectAssignmentRepository = subjectAssignmentRepository;
        this.departmentAffinityRepository = departmentAffinityRepository;
        this.similarityEngine = similarityEngine;
        this.cacheService = cacheService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public RecommendationResponseDTO getPersonalizedRecommendations(UUID userId, int requestedLimit) {
        int limit = clamp(requestedLimit, 1, 50);
        int historyLimit = clamp(properties.getHistoryLimit(), 1, 500);
        int candidatePool = clamp(properties.getCandidatePool(), 100, 10_000);
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        List<UserBookInteractionProjection> interactions = loanDetailRepository.findUserBookInteractions(
                userId, PageRequest.of(0, historyLimit));
        String departmentKey = DepartmentKeyNormalizer.normalize(user.getDepartment());
        String fingerprint = interactionFingerprint(departmentKey, interactions);
        var cached = cacheService.get(userId, limit, fingerprint);
        if (cached.isPresent()) {
            return cached.get();
        }

        Set<Long> borrowedBookIds = interactions.stream()
                .map(UserBookInteractionProjection::getBookId)
                .collect(Collectors.toCollection(HashSet::new));
        Map<Long, BookEntity> historyBooksById = bookRepository.findAllById(borrowedBookIds).stream()
                .collect(Collectors.toMap(BookEntity::getBookId, Function.identity()));
        List<TfIdfSimilarityEngine.WeightedBook> profileBooks = interactions.stream()
                .filter(interaction -> historyBooksById.containsKey(interaction.getBookId()))
                .map(interaction -> new TfIdfSimilarityEngine.WeightedBook(
                        historyBooksById.get(interaction.getBookId()),
                        interactionWeight(interaction)))
                .toList();

        List<DepartmentSubjectAffinityEntity> affinities = departmentKey.isEmpty()
                ? List.of()
                : departmentAffinityRepository.findByDepartmentKeyAndActiveTrueOrderByWeightDesc(departmentKey);
        Map<Long, Double> affinityBySubjectId = affinities.stream().collect(Collectors.toMap(
                affinity -> affinity.getSubject().getSubjectId(),
                affinity -> affinity.getWeight().doubleValue(),
                Math::max));

        List<BookSubjectAssignmentEntity> historyAssignments = borrowedBookIds.isEmpty()
                ? List.of()
                : subjectAssignmentRepository.findByBook_BookIdIn(borrowedBookIds);
        Set<Long> targetSubjectIds = new HashSet<>(affinityBySubjectId.keySet());
        historyAssignments.stream()
                .map(assignment -> assignment.getSubject().getSubjectId())
                .forEach(targetSubjectIds::add);

        LinkedHashMap<Long, BookEntity> candidatesById = new LinkedHashMap<>();
        if (!targetSubjectIds.isEmpty()) {
            addCandidates(
                    candidatesById,
                    subjectAssignmentRepository.findCandidateBooksBySubjects(
                            targetSubjectIds, PageRequest.of(0, candidatePool)),
                    borrowedBookIds,
                    candidatePool);
        }

        Set<Long> historyCategoryIds = historyBooksById.values().stream()
                .filter(book -> book.getCategory() != null)
                .map(book -> book.getCategory().getCategoryId())
                .collect(Collectors.toSet());
        if (!historyCategoryIds.isEmpty() && candidatesById.size() < candidatePool) {
            addCandidates(
                    candidatesById,
                    bookRepository.findByCategory_CategoryIdInOrderByBookIdAsc(
                            historyCategoryIds,
                            PageRequest.of(0, candidatePool - candidatesById.size())),
                    borrowedBookIds,
                    candidatePool);
        }

        LocalDateTime asOf = LocalDateTime.now();
        int popularityWindowDays = clamp(properties.getPopularityWindowDays(), 30, 3_650);
        List<BookPopularityProjection> popularity = loanDetailRepository.findPopularityScores(
                asOf.minusDays(popularityWindowDays),
                asOf,
                Math.max(30.0, popularityWindowDays / 4.0),
                PageRequest.of(0, Math.min(candidatePool, 1_000)));
        Map<Long, BookEntity> popularBooksById = bookRepository.findAllById(
                        popularity.stream().map(BookPopularityProjection::getBookId).toList())
                .stream()
                .collect(Collectors.toMap(BookEntity::getBookId, Function.identity()));
        addCandidates(
                candidatesById,
                popularity.stream().map(score -> popularBooksById.get(score.getBookId())).toList(),
                borrowedBookIds,
                candidatePool);

        if (candidatesById.size() < candidatePool) {
            addCandidates(
                    candidatesById,
                    bookRepository.findRecommendationFallback(PageRequest.of(
                            0, Math.min(candidatePool - candidatesById.size(), 1_000))),
                    borrowedBookIds,
                    candidatePool);
        }
        if (candidatesById.size() < Math.min(limit, 10)) {
            addCandidates(
                    candidatesById,
                    bookRepository.findCatalogFallback(PageRequest.of(0, Math.max(limit * 5, 50))),
                    borrowedBookIds,
                    candidatePool);
        }

        List<BookEntity> candidates = new ArrayList<>(candidatesById.values());
        Set<Long> allDocumentIds = new HashSet<>(borrowedBookIds);
        allDocumentIds.addAll(candidatesById.keySet());
        List<BookSubjectAssignmentEntity> allAssignments = allDocumentIds.isEmpty()
                ? List.of()
                : subjectAssignmentRepository.findByBook_BookIdIn(allDocumentIds);
        Map<Long, List<String>> subjectTerms = toSubjectTerms(allAssignments);
        Map<Long, List<BookSubjectAssignmentEntity>> assignmentsByBookId = allAssignments.stream()
                .collect(Collectors.groupingBy(assignment -> assignment.getBook().getBookId()));

        Map<Long, Double> contentScores = profileBooks.isEmpty()
                ? Map.of()
                : similarityEngine.scoreProfile(profileBooks, candidates, subjectTerms);
        Map<Long, Double> departmentScores = candidates.stream().collect(Collectors.toMap(
                BookEntity::getBookId,
                book -> departmentScore(
                        assignmentsByBookId.getOrDefault(book.getBookId(), List.of()),
                        affinityBySubjectId)));
        Map<Long, Double> popularityScores = normalizePopularity(popularity);
        Map<Long, Long> borrowCounts = popularity.stream().collect(Collectors.toMap(
                BookPopularityProjection::getBookId,
                BookPopularityProjection::getBorrowCount,
                Math::max));
        double[] weights = normalizedWeights();

        List<RecommendationItemDTO> items = candidates.stream()
                .map(book -> scoredItem(
                        book,
                        contentScores.getOrDefault(book.getBookId(), 0.0),
                        departmentScores.getOrDefault(book.getBookId(), 0.0),
                        popularityScores.getOrDefault(book.getBookId(), 0.0),
                        borrowCounts.getOrDefault(book.getBookId(), 0L),
                        weights,
                        user.getDepartment(),
                        profileBooks.isEmpty()))
                .sorted(Comparator
                        .comparingDouble(RecommendationItemDTO::score)
                        .reversed()
                        .thenComparing(RecommendationItemDTO::bookId))
                .limit(limit)
                .toList();

        boolean coldStart = profileBooks.isEmpty();
        RecommendationResponseDTO response = new RecommendationResponseDTO(
                coldStart ? "HYBRID_COLD_START" : "HYBRID_PERSONALIZED",
                !profileBooks.isEmpty() || !affinities.isEmpty(),
                candidates.size(),
                asOf,
                items);
        cacheService.put(userId, limit, fingerprint, response);
        return response;
    }

    private void addCandidates(
            LinkedHashMap<Long, BookEntity> target,
            Collection<BookEntity> books,
            Set<Long> excludedBookIds,
            int candidatePool) {
        for (BookEntity book : books) {
            if (target.size() >= candidatePool) {
                return;
            }
            if (book != null
                    && book.getBookId() != null
                    && book.getTitle() != null
                    && !book.getTitle().isBlank()
                    && !excludedBookIds.contains(book.getBookId())) {
                target.putIfAbsent(book.getBookId(), book);
            }
        }
    }

    private RecommendationItemDTO scoredItem(
            BookEntity book,
            double contentScore,
            double departmentScore,
            double popularityScore,
            long borrowCount,
            double[] weights,
            String department,
            boolean coldStart) {
        double contentContribution = weights[0] * contentScore;
        double departmentContribution = weights[1] * departmentScore;
        double popularityContribution = weights[2] * popularityScore;
        double hybridScore = contentContribution + departmentContribution + popularityContribution;
        String reason = reason(
                contentContribution,
                departmentContribution,
                popularityContribution,
                department,
                coldStart);
        String categoryName = book.getCategory() == null ? null : book.getCategory().getName();
        String imageUrl = firstNonBlank(book.getImageUrlM(), book.getImageUrlL(), book.getImageUrlS());
        long copies = bookCopyRepository.findByBookId(book.getBookId()).size();
        long availableCopies = bookCopyRepository.countByBookIdAndStatus(book.getBookId(), "AVAILABLE");
        return new RecommendationItemDTO(
                book.getBookId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPublisher(),
                categoryName,
                imageUrl,
                round(hybridScore),
                round(contentScore),
                round(departmentScore),
                round(popularityScore),
                borrowCount,
                reason,
                availableCopies,
                copies,
                availableCopies > 0 ? "available" : "out_of_stock");
    }

    private String reason(
            double contentContribution,
            double departmentContribution,
            double popularityContribution,
            String department,
            boolean coldStart) {
        if (departmentContribution >= contentContribution
                && departmentContribution >= popularityContribution
                && departmentContribution > 0) {
            return coldStart
                    ? "Gợi ý khởi đầu theo chuyên ngành " + displayDepartment(department)
                    : "Phù hợp chuyên ngành " + displayDepartment(department);
        }
        if (contentContribution >= popularityContribution && contentContribution > 0) {
            return "Tương tự nội dung các sách bạn đã mượn";
        }
        if (popularityContribution > 0) {
            return coldStart
                    ? "Gợi ý khởi đầu từ sách được mượn nhiều gần đây"
                    : "Được nhiều bạn đọc mượn gần đây";
        }
        return "Danh mục dự phòng trong lúc hệ thống tích lũy thêm dữ liệu";
    }

    private Map<Long, Double> normalizePopularity(List<BookPopularityProjection> popularity) {
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
                score -> Math.log1p(Math.max(score.getWeightedScore(), 0.0)) / Math.log1p(max),
                Math::max));
    }

    private double departmentScore(
            List<BookSubjectAssignmentEntity> assignments,
            Map<Long, Double> affinityBySubjectId) {
        return assignments.stream()
                .mapToDouble(assignment -> affinityBySubjectId.getOrDefault(
                                assignment.getSubject().getSubjectId(), 0.0)
                        * assignment.getConfidence().doubleValue())
                .max()
                .orElse(0.0);
    }

    private Map<Long, List<String>> toSubjectTerms(Collection<BookSubjectAssignmentEntity> assignments) {
        return assignments.stream().collect(Collectors.groupingBy(
                assignment -> assignment.getBook().getBookId(),
                Collectors.mapping(
                        assignment -> assignment.getSubject().getCode() + " " + assignment.getSubject().getName(),
                        Collectors.toList())));
    }

    private double interactionWeight(UserBookInteractionProjection interaction) {
        LocalDateTime lastInteraction = interaction.getLastInteractedAt();
        long ageDays = lastInteraction == null
                ? 365
                : Math.max(0, Duration.between(lastInteraction, LocalDateTime.now()).toDays());
        double recency = Math.pow(0.5, ageDays / 180.0);
        double frequency = 1.0 + Math.log1p(Math.max(1, interaction.getBorrowCount()));
        double renewal = 1.0 + 0.25 * Math.max(0, interaction.getRenewalCount());
        return recency * frequency * renewal;
    }

    private double[] normalizedWeights() {
        double content = Math.max(0.0, properties.getContentWeight());
        double department = Math.max(0.0, properties.getDepartmentWeight());
        double popularity = Math.max(0.0, properties.getPopularityWeight());
        double total = content + department + popularity;
        if (total == 0) {
            return new double[]{0.50, 0.35, 0.15};
        }
        return new double[]{content / total, department / total, popularity / total};
    }

    private String interactionFingerprint(
            String departmentKey,
            List<UserBookInteractionProjection> interactions) {
        StringBuilder source = new StringBuilder(departmentKey).append('|');
        interactions.forEach(interaction -> source
                .append(interaction.getBookId()).append(':')
                .append(interaction.getBorrowCount()).append(':')
                .append(interaction.getRenewalCount()).append(':')
                .append(interaction.getLastInteractedAt()).append(';'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.toString().getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private String displayDepartment(String department) {
        return department == null || department.isBlank() ? "của bạn" : department.trim();
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
