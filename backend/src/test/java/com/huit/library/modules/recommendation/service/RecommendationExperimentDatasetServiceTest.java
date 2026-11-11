package com.huit.library.modules.recommendation.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.circulation.repository.EvaluationInteractionProjection;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Interaction;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Split;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.UserCohort;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationExperimentDatasetServiceTest {

    private final LoanDetailRepository loanDetailRepository = mock(LoanDetailRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final BookRepository bookRepository = mock(BookRepository.class);
    private final RecommendationExperimentDatasetService service =
            new RecommendationExperimentDatasetService(
                    loanDetailRepository, userRepository, bookRepository);

    @Test
    void buildsDeterministicChronologicalDatasetWithoutFutureLeakage() {
        UUID warmUser = uuid(1);
        UUID sparseUser = uuid(2);
        UUID singleInteractionUser = uuid(3);
        UUID noHistoryUser = uuid(4);
        when(userRepository.findByUserTypeOrderByFullNameAsc("STUDENT"))
                .thenReturn(
                        List.of(
                                student(warmUser),
                                student(sparseUser),
                                student(singleInteractionUser),
                                student(noHistoryUser)));

        List<BookEntity> books = new ArrayList<>();
        for (long bookId = 1; bookId <= 9; bookId++) {
            books.add(book(bookId, bookId != 9));
        }
        when(bookRepository.findAll()).thenReturn(books);
        when(bookRepository.findBookIdsWithActiveDepartmentMapping())
                .thenReturn(List.of(1L, 2L, 3L, 4L));

        LocalDateTime start = LocalDateTime.of(2026, 1, 1, 8, 0);
        List<EvaluationInteractionProjection> rows = new ArrayList<>();
        for (long bookId = 1; bookId <= 5; bookId++) {
            rows.add(interaction(warmUser, bookId, start.plusDays(bookId), bookId == 1 ? 1 : 0));
        }
        rows.add(interaction(warmUser, 1L, start.plusDays(10), 0));
        rows.add(interaction(sparseUser, 6L, start.plusDays(1), 0));
        rows.add(interaction(sparseUser, 7L, start.plusDays(2), 0));
        rows.add(interaction(singleInteractionUser, 8L, start.plusDays(3), 0));
        rows.add(interaction(uuid(99), 1L, start, 0));
        rows.add(interaction(warmUser, 999L, start, 0));
        when(loanDetailRepository.findEvaluationInteractions()).thenReturn(rows);

        RecommendationExperimentDataset dataset = service.buildDataset();

        assertThat(dataset.datasetName())
                .isEqualTo(RecommendationExperimentDatasetService.DATASET_NAME);
        assertThat(dataset.summary().totalStudents()).isEqualTo(4);
        assertThat(dataset.summary().totalEligibleBooks()).isEqualTo(9);
        assertThat(dataset.summary().validRawInteractions()).isEqualTo(9);
        assertThat(dataset.summary().uniqueUserBookInteractions()).isEqualTo(8);
        assertThat(dataset.summary().trainInteractions()).isEqualTo(4);
        assertThat(dataset.summary().validationInteractions()).isEqualTo(1);
        assertThat(dataset.summary().testInteractions()).isEqualTo(3);
        assertThat(dataset.summary().warmUsers()).isEqualTo(1);
        assertThat(dataset.summary().sparseUsers()).isEqualTo(1);
        assertThat(dataset.summary().newUsers()).isEqualTo(2);
        assertThat(dataset.summary().newBooks()).isEqualTo(5);
        assertThat(dataset.summary().studentsWithoutHistoryPercent()).isEqualTo(25.0);
        assertThat(dataset.summary().booksNeverBorrowedPercent()).isEqualTo(11.111111);
        assertThat(dataset.summary().sparsity()).isEqualTo(0.777778);
        assertThat(dataset.newUserIds()).containsExactly(singleInteractionUser, noHistoryUser);
        assertThat(dataset.newBookIds()).containsExactly(4L, 5L, 7L, 8L, 9L);

        Interaction repeatedPair = find(dataset, warmUser, 1L);
        assertThat(repeatedPair.occurrenceCount()).isEqualTo(2);
        assertThat(repeatedPair.renewalCount()).isEqualTo(1);
        assertThat(repeatedPair.preferenceWeight()).isEqualTo(1.25);
        assertThat(repeatedPair.split()).isEqualTo(Split.TRAIN);
        assertThat(repeatedPair.cohort()).isEqualTo(UserCohort.WARM);

        assertThat(find(dataset, warmUser, 4L).split()).isEqualTo(Split.VALIDATION);
        assertThat(find(dataset, warmUser, 5L).split()).isEqualTo(Split.TEST);
        assertThat(find(dataset, sparseUser, 6L).cohort()).isEqualTo(UserCohort.SPARSE);
        assertThat(find(dataset, singleInteractionUser, 8L).cohort()).isEqualTo(UserCohort.NEW);

        dataset.interactions().stream()
                .map(Interaction::userId)
                .distinct()
                .forEach(
                        userId -> {
                            LocalDateTime latestTrain =
                                    dataset.interactions().stream()
                                            .filter(
                                                    row ->
                                                            row.userId().equals(userId)
                                                                    && row.split() == Split.TRAIN)
                                            .map(Interaction::firstInteractionAt)
                                            .max(LocalDateTime::compareTo)
                                            .orElse(null);
                            LocalDateTime earliestHoldout =
                                    dataset.interactions().stream()
                                            .filter(
                                                    row ->
                                                            row.userId().equals(userId)
                                                                    && row.split() != Split.TRAIN)
                                            .map(Interaction::firstInteractionAt)
                                            .min(LocalDateTime::compareTo)
                                            .orElse(null);
                            if (latestTrain != null && earliestHoldout != null) {
                                assertThat(latestTrain).isBeforeOrEqualTo(earliestHoldout);
                            }
                        });
    }

    private Interaction find(RecommendationExperimentDataset dataset, UUID userId, long bookId) {
        return dataset.interactions().stream()
                .filter(row -> row.userId().equals(userId) && row.bookId().equals(bookId))
                .findFirst()
                .orElseThrow();
    }

    private UserEntity student(UUID userId) {
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setUserType("STUDENT");
        return user;
    }

    private BookEntity book(long bookId, boolean completeMetadata) {
        BookEntity book = new BookEntity();
        book.setBookId(bookId);
        book.setTitle("Book " + bookId);
        book.setAuthor(completeMetadata ? "Author " + bookId : " ");
        book.setDescription(completeMetadata ? "Description " + bookId : null);
        return book;
    }

    private EvaluationInteractionProjection interaction(
            UUID userId, Long bookId, LocalDateTime interactionAt, Integer renewalCount) {
        EvaluationInteractionProjection projection = mock(EvaluationInteractionProjection.class);
        when(projection.getUserId()).thenReturn(userId);
        when(projection.getBookId()).thenReturn(bookId);
        when(projection.getInteractionAt()).thenReturn(interactionAt);
        when(projection.getRenewalCount()).thenReturn(renewalCount);
        return projection;
    }

    private UUID uuid(long suffix) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(suffix));
    }
}
