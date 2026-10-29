package com.huit.library.modules.recommendation.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.entity.CategoryEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.circulation.repository.BookPopularityProjection;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.recommendation.dto.RecommendationResponseDTO;
import com.huit.library.modules.recommendation.repository.BookSubjectAssignmentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BookRecommendationServiceTest {
    @Test
    void popularityBaselineNormalizesScoresAndFillsSparseHistoryFromCatalog() {
        BookRepository bookRepository = mock(BookRepository.class);
        LoanDetailRepository loanRepository = mock(LoanDetailRepository.class);
        BookSubjectAssignmentRepository assignmentRepository = mock(BookSubjectAssignmentRepository.class);
        BookRecommendationService service = new BookRecommendationService(
                bookRepository, mock(BookCopyRepository.class), loanRepository, assignmentRepository, new TfIdfSimilarityEngine());
        BookEntity popular = book(1L, "Java", "Công nghệ thông tin");
        BookEntity fallback = book(2L, "Cơ sở dữ liệu", "Công nghệ thông tin");

        when(loanRepository.findPopularityScores(any(), any(), anyDouble(), any(Pageable.class)))
                .thenReturn(List.of(popularity(1L, 4, 3.5)));
        when(bookRepository.findAllById(any())).thenReturn(List.of(popular));
        when(bookRepository.findRecommendationFallback(any(Pageable.class))).thenReturn(List.of(popular, fallback));

        RecommendationResponseDTO response = service.getPopularBooks(2, 365);

        assertThat(response.method()).isEqualTo("POPULARITY_BASELINE");
        assertThat(response.dataSufficient()).isFalse();
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).bookId()).isEqualTo(1L);
        assertThat(response.items().get(0).score()).isEqualTo(1.0);
        assertThat(response.items().get(1).reason()).contains("dự phòng");
    }

    @Test
    void similarBooksUsesTfIdfAndExcludesSourceBook() {
        BookRepository bookRepository = mock(BookRepository.class);
        LoanDetailRepository loanRepository = mock(LoanDetailRepository.class);
        BookSubjectAssignmentRepository assignmentRepository = mock(BookSubjectAssignmentRepository.class);
        BookRecommendationService service = new BookRecommendationService(
                bookRepository, mock(BookCopyRepository.class), loanRepository, assignmentRepository, new TfIdfSimilarityEngine());
        BookEntity source = book(1L, "Lập trình Java căn bản", "Công nghệ thông tin");
        BookEntity relevant = book(2L, "Lập trình Java Spring", "Công nghệ thông tin");
        BookEntity unrelated = book(3L, "Hóa sinh thực phẩm", "Công nghệ thông tin");

        when(bookRepository.findById(1L)).thenReturn(Optional.of(source));
        when(assignmentRepository.findByBook_BookIdOrderByConfidenceDesc(1L)).thenReturn(List.of());
        when(bookRepository.findByCategory_CategoryIdAndBookIdNotOrderByBookIdAsc(
                any(), any(), any(Pageable.class))).thenReturn(List.of(relevant, unrelated));
        when(bookRepository.findByBookIdNotOrderByBookIdAsc(any(), any(Pageable.class))).thenReturn(List.of());
        when(assignmentRepository.findByBook_BookIdIn(any())).thenReturn(List.of());

        RecommendationResponseDTO response = service.getSimilarBooks(1L, 10);

        assertThat(response.method()).isEqualTo("TF_IDF_COSINE");
        assertThat(response.items()).isNotEmpty();
        assertThat(response.items().get(0).bookId()).isEqualTo(2L);
        assertThat(response.items()).noneMatch(item -> item.bookId().equals(1L));
    }

    private BookEntity book(Long id, String title, String categoryName) {
        CategoryEntity category = new CategoryEntity();
        category.setCategoryId(9L);
        category.setName(categoryName);
        BookEntity book = new BookEntity();
        book.setBookId(id);
        book.setTitle(title);
        book.setCategory(category);
        return book;
    }

    private BookPopularityProjection popularity(Long bookId, long borrowCount, double weightedScore) {
        return new BookPopularityProjection() {
            @Override
            public Long getBookId() {
                return bookId;
            }

            @Override
            public long getBorrowCount() {
                return borrowCount;
            }

            @Override
            public double getWeightedScore() {
                return weightedScore;
            }

            @Override
            public LocalDateTime getLastBorrowedAt() {
                return LocalDateTime.now();
            }
        };
    }
}
