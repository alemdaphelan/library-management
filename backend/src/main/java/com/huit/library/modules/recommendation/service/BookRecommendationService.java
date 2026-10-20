package com.huit.library.modules.recommendation.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.circulation.repository.BookPopularityProjection;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.recommendation.dto.RecommendationItemDTO;
import com.huit.library.modules.recommendation.dto.RecommendationResponseDTO;
import com.huit.library.modules.recommendation.entity.BookSubjectAssignmentEntity;
import com.huit.library.modules.recommendation.repository.BookSubjectAssignmentRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookRecommendationService {
    private static final int DEFAULT_CANDIDATE_POOL = 1_500;
    private static final int MIN_POPULAR_BOOKS_FOR_SUFFICIENT_DATA = 10;
    private static final long MIN_BORROWS_FOR_SUFFICIENT_DATA = 20;

    private final BookRepository bookRepository;
    private final LoanDetailRepository loanDetailRepository;
    private final BookSubjectAssignmentRepository subjectAssignmentRepository;
    private final TfIdfSimilarityEngine similarityEngine;

    public BookRecommendationService(
            BookRepository bookRepository,
            LoanDetailRepository loanDetailRepository,
            BookSubjectAssignmentRepository subjectAssignmentRepository,
            TfIdfSimilarityEngine similarityEngine) {
        this.bookRepository = bookRepository;
        this.loanDetailRepository = loanDetailRepository;
        this.subjectAssignmentRepository = subjectAssignmentRepository;
        this.similarityEngine = similarityEngine;
    }

    @Transactional(readOnly = true)
    public RecommendationResponseDTO getPopularBooks(int requestedLimit, int requestedWindowDays) {
        int limit = clamp(requestedLimit, 1, 50);
        int windowDays = clamp(requestedWindowDays, 30, 3_650);
        LocalDateTime asOf = LocalDateTime.now();
        LocalDateTime since = asOf.minusDays(windowDays);
        double halfLifeDays = Math.max(30.0, windowDays / 4.0);
        int queryLimit = Math.max(100, limit * 10);

        List<BookPopularityProjection> popularity = loanDetailRepository.findPopularityScores(
                since, asOf, halfLifeDays, PageRequest.of(0, queryLimit));
        Map<Long, BookEntity> booksById = bookRepository.findAllById(
                        popularity.stream().map(BookPopularityProjection::getBookId).toList())
                .stream()
                .collect(Collectors.toMap(BookEntity::getBookId, Function.identity()));

        double maxWeightedScore = popularity.stream()
                .mapToDouble(BookPopularityProjection::getWeightedScore)
                .filter(Double::isFinite)
                .max()
                .orElse(0.0);
        List<RecommendationItemDTO> items = new ArrayList<>();
        Set<Long> usedBookIds = new java.util.HashSet<>();
        for (BookPopularityProjection score : popularity) {
            BookEntity book = booksById.get(score.getBookId());
            if (book == null || !hasUsableTitle(book) || items.size() >= limit) {
                continue;
            }
            double normalizedScore = maxWeightedScore > 0
                    ? Math.log1p(Math.max(score.getWeightedScore(), 0.0)) / Math.log1p(maxWeightedScore)
                    : 0.0;
            items.add(toItem(
                    book,
                    normalizedScore,
                    0.0,
                    0.0,
                    normalizedScore,
                    score.getBorrowCount(),
                    "Được mượn " + score.getBorrowCount() + " lượt trong " + windowDays + " ngày gần đây"));
            usedBookIds.add(book.getBookId());
        }

        if (items.size() < limit) {
            appendFallbackItems(
                    items,
                    usedBookIds,
                    bookRepository.findRecommendationFallback(PageRequest.of(0, Math.max(limit * 5, 50))),
                    limit);
        }
        if (items.size() < limit) {
            appendFallbackItems(
                    items,
                    usedBookIds,
                    bookRepository.findCatalogFallback(PageRequest.of(0, Math.max(limit * 5, 50))),
                    limit);
        }

        long totalBorrows = popularity.stream().mapToLong(BookPopularityProjection::getBorrowCount).sum();
        boolean dataSufficient = popularity.size() >= MIN_POPULAR_BOOKS_FOR_SUFFICIENT_DATA
                && totalBorrows >= MIN_BORROWS_FOR_SUFFICIENT_DATA;
        return new RecommendationResponseDTO(
                "POPULARITY_BASELINE",
                dataSufficient,
                popularity.size(),
                asOf,
                List.copyOf(items));
    }

    @Transactional(readOnly = true)
    public RecommendationResponseDTO getSimilarBooks(Long sourceBookId, int requestedLimit) {
        int limit = clamp(requestedLimit, 1, 50);
        BookEntity source = bookRepository.findById(sourceBookId)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + sourceBookId));
        List<BookSubjectAssignmentEntity> sourceAssignments =
                subjectAssignmentRepository.findByBook_BookIdOrderByConfidenceDesc(sourceBookId);

        LinkedHashMap<Long, BookEntity> candidatesById = new LinkedHashMap<>();
        List<Long> subjectIds = sourceAssignments.stream()
                .map(assignment -> assignment.getSubject().getSubjectId())
                .distinct()
                .toList();
        if (!subjectIds.isEmpty()) {
            subjectAssignmentRepository.findCandidateBooksSharingSubjects(
                            subjectIds,
                            sourceBookId,
                            PageRequest.of(0, DEFAULT_CANDIDATE_POOL))
                    .forEach(book -> candidatesById.putIfAbsent(book.getBookId(), book));
        }

        int remainingCapacity = DEFAULT_CANDIDATE_POOL - candidatesById.size();
        if (remainingCapacity > 0 && source.getCategory() != null) {
            bookRepository.findByCategory_CategoryIdAndBookIdNotOrderByBookIdAsc(
                            source.getCategory().getCategoryId(),
                            sourceBookId,
                            PageRequest.of(0, remainingCapacity))
                    .forEach(book -> candidatesById.putIfAbsent(book.getBookId(), book));
        }
        remainingCapacity = DEFAULT_CANDIDATE_POOL - candidatesById.size();
        if (remainingCapacity > 0) {
            bookRepository.findByBookIdNotOrderByBookIdAsc(
                            sourceBookId,
                            PageRequest.of(0, remainingCapacity))
                    .forEach(book -> candidatesById.putIfAbsent(book.getBookId(), book));
        }

        List<BookEntity> candidates = candidatesById.values().stream()
                .filter(this::hasUsableTitle)
                .toList();
        List<Long> documentIds = candidates.stream().map(BookEntity::getBookId).collect(Collectors.toCollection(ArrayList::new));
        documentIds.add(sourceBookId);
        Map<Long, List<String>> subjectTerms = toSubjectTerms(
                subjectAssignmentRepository.findByBook_BookIdIn(documentIds));
        Map<Long, Double> similarityScores = similarityEngine.score(source, candidates, subjectTerms);

        List<RecommendationItemDTO> items = candidates.stream()
                .filter(candidate -> similarityScores.containsKey(candidate.getBookId()))
                .sorted(Comparator
                        .comparingDouble((BookEntity candidate) -> similarityScores.get(candidate.getBookId()))
                        .reversed()
                        .thenComparing(BookEntity::getBookId))
                .limit(limit)
                .map(candidate -> toItem(
                        candidate,
                        similarityScores.get(candidate.getBookId()),
                        similarityScores.get(candidate.getBookId()),
                        0.0,
                        0.0,
                        0,
                        "Nội dung tương tự sách “" + safe(source.getTitle()) + "” theo TF-IDF/Cosine"))
                .toList();

        return new RecommendationResponseDTO(
                "TF_IDF_COSINE",
                candidates.size() >= 20 && items.size() >= Math.min(limit, 5),
                candidates.size(),
                LocalDateTime.now(),
                items);
    }

    private Map<Long, List<String>> toSubjectTerms(Collection<BookSubjectAssignmentEntity> assignments) {
        return assignments.stream().collect(Collectors.groupingBy(
                assignment -> assignment.getBook().getBookId(),
                Collectors.mapping(
                        assignment -> assignment.getSubject().getCode() + " " + assignment.getSubject().getName(),
                        Collectors.toList())));
    }

    private RecommendationItemDTO toItem(
            BookEntity book,
            double score,
            double contentScore,
            double departmentScore,
            double popularityScore,
            long borrowCount,
            String reason) {
        String categoryName = book.getCategory() == null ? null : book.getCategory().getName();
        String imageUrl = firstNonBlank(book.getImageUrlM(), book.getImageUrlL(), book.getImageUrlS());
        return new RecommendationItemDTO(
                book.getBookId(),
                book.getTitle(),
                book.getAuthor(),
                book.getPublisher(),
                categoryName,
                imageUrl,
                round(score),
                round(contentScore),
                round(departmentScore),
                round(popularityScore),
                borrowCount,
                reason);
    }

    private void appendFallbackItems(
            List<RecommendationItemDTO> items,
            Set<Long> usedBookIds,
            List<BookEntity> fallbackBooks,
            int limit) {
        for (BookEntity book : fallbackBooks) {
            if (items.size() >= limit) {
                return;
            }
            if (hasUsableTitle(book) && usedBookIds.add(book.getBookId())) {
                items.add(toItem(
                        book,
                        0.0,
                        0.0,
                        0.0,
                        0.0,
                        0,
                        "Danh mục đã phân loại được dùng dự phòng vì lịch sử mượn hiện chưa đủ"));
            }
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

    private String safe(String value) {
        return value == null || value.isBlank() ? "đang xem" : value;
    }

    private boolean hasUsableTitle(BookEntity book) {
        return book.getTitle() != null && !book.getTitle().isBlank();
    }

    private int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(value, maximum));
    }

    private double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }
}
