package com.huit.library.modules.recommendation.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.entity.CategoryEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.UserBookInteractionProjection;
import com.huit.library.modules.recommendation.config.RecommendationProperties;
import com.huit.library.modules.recommendation.dto.RecommendationResponseDTO;
import com.huit.library.modules.recommendation.entity.BookSubjectAssignmentEntity;
import com.huit.library.modules.recommendation.entity.DepartmentSubjectAffinityEntity;
import com.huit.library.modules.recommendation.entity.RecommendationSubjectEntity;
import com.huit.library.modules.recommendation.repository.BookSubjectAssignmentRepository;
import com.huit.library.modules.recommendation.repository.DepartmentSubjectAffinityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridRecommendationServiceTest {
    @Test
    void coldStartRanksDepartmentBookAndExplainsReason() {
        Fixture fixture = new Fixture();
        UUID userId = UUID.randomUUID();
        UserEntity user = user(userId, "Công nghệ thông tin");
        RecommendationSubjectEntity itSubject = subject(10L, "IT_DATA_AI", "Công nghệ thông tin");
        BookEntity itBook = book(2L, "Lập trình Java", "Công nghệ thông tin");
        BookEntity literatureBook = book(3L, "Văn học Việt Nam", "Văn học");

        DepartmentSubjectAffinityEntity affinity = new DepartmentSubjectAffinityEntity();
        affinity.setSubject(itSubject);
        affinity.setWeight(BigDecimal.ONE);
        BookSubjectAssignmentEntity assignment = assignment(itBook, itSubject, "1.000");

        when(fixture.userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fixture.loanRepository.findUserBookInteractions(any(), any(Pageable.class))).thenReturn(List.of());
        when(fixture.cacheService.get(any(), anyInt(), any())).thenReturn(Optional.empty());
        when(fixture.departmentRepository.findByDepartmentKeyAndActiveTrueOrderByWeightDesc("cong nghe thong tin"))
                .thenReturn(List.of(affinity));
        when(fixture.subjectRepository.findCandidateBooksBySubjects(any(), any(Pageable.class)))
                .thenReturn(List.of(itBook));
        when(fixture.bookRepository.findRecommendationFallback(any(Pageable.class)))
                .thenReturn(List.of(literatureBook));
        when(fixture.subjectRepository.findByBook_BookIdIn(any())).thenReturn(List.of(assignment));

        RecommendationResponseDTO response = fixture.service.getPersonalizedRecommendations(userId, 2);

        assertThat(response.method()).isEqualTo("HYBRID_COLD_START");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).bookId()).isEqualTo(2L);
        assertThat(response.items().get(0).departmentScore()).isEqualTo(1.0);
        assertThat(response.items().get(0).reason()).contains("chuyên ngành");
        verify(fixture.cacheService).put(any(), anyInt(), any(), any());
    }

    @Test
    void personalizedRankingExcludesPreviouslyBorrowedBooks() {
        Fixture fixture = new Fixture();
        UUID userId = UUID.randomUUID();
        UserEntity user = user(userId, null);
        BookEntity borrowed = book(1L, "Lập trình Java căn bản", "Công nghệ thông tin");
        BookEntity relevant = book(2L, "Java Spring thực hành", "Công nghệ thông tin");
        BookEntity unrelated = book(3L, "Hóa sinh thực phẩm", "Công nghệ thông tin");
        UserBookInteractionProjection interaction = interaction(1L, 2, 1);

        when(fixture.userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fixture.loanRepository.findUserBookInteractions(any(), any(Pageable.class)))
                .thenReturn(List.of(interaction));
        when(fixture.cacheService.get(any(), anyInt(), any())).thenReturn(Optional.empty());
        when(fixture.bookRepository.findAllById(any())).thenAnswer(invocation -> {
            List<Long> ids = new ArrayList<>();
            invocation.<Iterable<Long>>getArgument(0).forEach(ids::add);
            return ids.contains(1L) ? List.of(borrowed) : List.of();
        });
        when(fixture.subjectRepository.findByBook_BookIdIn(any())).thenReturn(List.of());
        when(fixture.bookRepository.findByCategory_CategoryIdInOrderByBookIdAsc(any(), any(Pageable.class)))
                .thenReturn(List.of(borrowed, relevant, unrelated));

        RecommendationResponseDTO response = fixture.service.getPersonalizedRecommendations(userId, 2);

        assertThat(response.method()).isEqualTo("HYBRID_PERSONALIZED");
        assertThat(response.items()).noneMatch(item -> item.bookId().equals(1L));
        assertThat(response.items().get(0).bookId()).isEqualTo(2L);
        assertThat(response.items().get(0).contentScore()).isPositive();
    }

    private static class Fixture {
        private final BookRepository bookRepository = mock(BookRepository.class);
        private final com.huit.library.modules.catalog.repository.BookCopyRepository bookCopyRepository = mock(com.huit.library.modules.catalog.repository.BookCopyRepository.class);
        private final LoanDetailRepository loanRepository = mock(LoanDetailRepository.class);
        private final UserRepository userRepository = mock(UserRepository.class);
        private final BookSubjectAssignmentRepository subjectRepository = mock(BookSubjectAssignmentRepository.class);
        private final DepartmentSubjectAffinityRepository departmentRepository = mock(DepartmentSubjectAffinityRepository.class);
        private final RecommendationCacheService cacheService = mock(RecommendationCacheService.class);
        private final RecommendationProperties properties = new RecommendationProperties();
        private final HybridRecommendationService service = new HybridRecommendationService(
                bookRepository,
                bookCopyRepository,
                loanRepository,
                userRepository,
                subjectRepository,
                departmentRepository,
                new TfIdfSimilarityEngine(),
                cacheService,
                properties);
    }

    private UserEntity user(UUID id, String department) {
        UserEntity user = new UserEntity();
        user.setUserId(id);
        user.setDepartment(department);
        return user;
    }

    private RecommendationSubjectEntity subject(Long id, String code, String name) {
        RecommendationSubjectEntity subject = new RecommendationSubjectEntity();
        subject.setSubjectId(id);
        subject.setCode(code);
        subject.setName(name);
        return subject;
    }

    private BookSubjectAssignmentEntity assignment(
            BookEntity book,
            RecommendationSubjectEntity subject,
            String confidence) {
        BookSubjectAssignmentEntity assignment = new BookSubjectAssignmentEntity();
        assignment.setBook(book);
        assignment.setSubject(subject);
        assignment.setConfidence(new BigDecimal(confidence));
        return assignment;
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

    private UserBookInteractionProjection interaction(Long bookId, long borrowCount, long renewalCount) {
        return new UserBookInteractionProjection() {
            @Override
            public Long getBookId() {
                return bookId;
            }

            @Override
            public long getBorrowCount() {
                return borrowCount;
            }

            @Override
            public long getRenewalCount() {
                return renewalCount;
            }

            @Override
            public LocalDateTime getLastInteractedAt() {
                return LocalDateTime.now().minusDays(2);
            }
        };
    }
}
