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
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Summary;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.UserCohort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationExperimentDatasetService {

    public static final String DATASET_NAME = "HUIT_LIBRARY_TIME_SPLIT_V1";
    public static final String SPLIT_STRATEGY =
            "Per user chronological leave-last-two-out: >=3 => last validation + last test; "
                    + "2 => first train + last test; 1 => test only";

    private final LoanDetailRepository loanDetailRepository;
    private final UserRepository userRepository;
    private final BookRepository bookRepository;
    private final Clock clock = Clock.systemUTC();

    @Transactional(readOnly = true)
    public RecommendationExperimentDataset buildDataset() {
        List<UserEntity> students = userRepository.findByUserTypeOrderByFullNameAsc("STUDENT");
        List<BookEntity> eligibleBooks =
                bookRepository.findAll().stream()
                        .filter(book -> book.getBookId() != null)
                        .filter(book -> hasText(book.getTitle()))
                        .sorted(Comparator.comparing(BookEntity::getBookId))
                        .toList();

        Set<UUID> studentIds =
                students.stream()
                        .map(UserEntity::getUserId)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toCollection(HashSet::new));
        Set<Long> bookIds =
                eligibleBooks.stream()
                        .map(BookEntity::getBookId)
                        .collect(Collectors.toCollection(HashSet::new));

        List<EvaluationInteractionProjection> raw =
                loanDetailRepository.findEvaluationInteractions();
        List<EvaluationInteractionProjection> validRaw =
                raw.stream()
                        .filter(
                                row ->
                                        row.getUserId() != null
                                                && studentIds.contains(row.getUserId()))
                        .filter(row -> row.getBookId() != null && bookIds.contains(row.getBookId()))
                        .filter(row -> row.getInteractionAt() != null)
                        .toList();

        Map<UserBookKey, PairAccumulator> uniquePairs = new LinkedHashMap<>();
        for (EvaluationInteractionProjection row : validRaw) {
            UserBookKey key = new UserBookKey(row.getUserId(), row.getBookId());
            uniquePairs
                    .computeIfAbsent(key, ignored -> new PairAccumulator(key))
                    .add(row.getInteractionAt(), safeNonNegative(row.getRenewalCount()));
        }

        Map<UUID, List<PairAccumulator>> byUser =
                uniquePairs.values().stream()
                        .collect(
                                Collectors.groupingBy(
                                        pair -> pair.key.userId(),
                                        LinkedHashMap::new,
                                        Collectors.toCollection(ArrayList::new)));
        Map<UserBookKey, Split> splitByPair = new HashMap<>();
        Map<UUID, UserCohort> cohortByUser = new HashMap<>();

        for (UUID studentId : studentIds) {
            List<PairAccumulator> history =
                    byUser.getOrDefault(studentId, List.of()).stream()
                            .sorted(
                                    Comparator.comparing(
                                                    (PairAccumulator pair) ->
                                                            pair.firstInteractionAt)
                                            .thenComparing(pair -> pair.key.bookId()))
                            .toList();
            assignChronologicalSplits(history, splitByPair);
            long trainCount =
                    history.stream()
                            .filter(pair -> splitByPair.get(pair.key) == Split.TRAIN)
                            .count();
            cohortByUser.put(studentId, cohortForTrainCount(trainCount));
        }

        List<Interaction> interactions =
                uniquePairs.values().stream()
                        .map(
                                pair ->
                                        pair.toInteraction(
                                                splitByPair.get(pair.key),
                                                cohortByUser.getOrDefault(
                                                        pair.key.userId(), UserCohort.NEW)))
                        .sorted(
                                Comparator.comparing(Interaction::userId)
                                        .thenComparing(Interaction::firstInteractionAt)
                                        .thenComparing(Interaction::bookId))
                        .toList();
        validateNoLeakage(interactions);

        Set<UUID> usersWithHistory =
                interactions.stream().map(Interaction::userId).collect(Collectors.toSet());
        Set<Long> borrowedBooks =
                interactions.stream().map(Interaction::bookId).collect(Collectors.toSet());
        Set<Long> trainBooks =
                interactions.stream()
                        .filter(interaction -> interaction.split() == Split.TRAIN)
                        .map(Interaction::bookId)
                        .collect(Collectors.toSet());
        Set<Long> departmentMappedBooks =
                new HashSet<>(bookRepository.findBookIdsWithActiveDepartmentMapping());

        List<UUID> newUserIds =
                studentIds.stream()
                        .filter(
                                userId ->
                                        cohortByUser.getOrDefault(userId, UserCohort.NEW)
                                                == UserCohort.NEW)
                        .sorted()
                        .toList();
        List<Long> newBookIds =
                bookIds.stream().filter(Predicate.not(trainBooks::contains)).sorted().toList();

        Summary summary =
                new Summary(
                        studentIds.size(),
                        bookIds.size(),
                        validRaw.size(),
                        interactions.size(),
                        ratio(interactions.size(), studentIds.size()),
                        ratio(interactions.size(), bookIds.size()),
                        percent(studentIds.size() - usersWithHistory.size(), studentIds.size()),
                        percent(bookIds.size() - borrowedBooks.size(), bookIds.size()),
                        percent(
                                countMissing(eligibleBooks, book -> book.getCategory() == null),
                                bookIds.size()),
                        percent(
                                countMissing(eligibleBooks, book -> !hasText(book.getAuthor())),
                                bookIds.size()),
                        percent(
                                bookIds.size()
                                        - departmentMappedBooks.stream()
                                                .filter(bookIds::contains)
                                                .count(),
                                bookIds.size()),
                        percent(
                                countMissing(
                                        eligibleBooks, book -> !hasText(book.getDescription())),
                                bookIds.size()),
                        sparsity(interactions.size(), studentIds.size(), bookIds.size()),
                        countSplit(interactions, Split.TRAIN),
                        countSplit(interactions, Split.VALIDATION),
                        countSplit(interactions, Split.TEST),
                        countCohort(cohortByUser, UserCohort.WARM),
                        countCohort(cohortByUser, UserCohort.SPARSE),
                        newUserIds.size(),
                        newBookIds.size());

        return new RecommendationExperimentDataset(
                DATASET_NAME,
                Instant.now(clock),
                SPLIT_STRATEGY,
                summary,
                interactions,
                newUserIds,
                newBookIds);
    }

    private void assignChronologicalSplits(
            List<PairAccumulator> history, Map<UserBookKey, Split> splitByPair) {
        int size = history.size();
        for (int index = 0; index < size; index++) {
            Split split;
            if (size == 1 || index == size - 1) {
                split = Split.TEST;
            } else if (size >= 3 && index == size - 2) {
                split = Split.VALIDATION;
            } else {
                split = Split.TRAIN;
            }
            splitByPair.put(history.get(index).key, split);
        }
    }

    private void validateNoLeakage(List<Interaction> interactions) {
        Set<UserBookKey> seenPairs = new HashSet<>();
        Map<UUID, LocalDateTime> latestTrain = new HashMap<>();
        Map<UUID, LocalDateTime> earliestHoldout = new HashMap<>();
        for (Interaction interaction : interactions) {
            if (!seenPairs.add(new UserBookKey(interaction.userId(), interaction.bookId()))) {
                throw new IllegalStateException(
                        "A user-book pair occurs in more than one dataset split");
            }
            if (interaction.split() == Split.TRAIN) {
                latestTrain.merge(
                        interaction.userId(),
                        interaction.firstInteractionAt(),
                        (left, right) -> left.isAfter(right) ? left : right);
            } else {
                earliestHoldout.merge(
                        interaction.userId(),
                        interaction.firstInteractionAt(),
                        (left, right) -> left.isBefore(right) ? left : right);
            }
        }
        latestTrain.forEach(
                (userId, trainTime) -> {
                    LocalDateTime holdoutTime = earliestHoldout.get(userId);
                    if (holdoutTime != null && trainTime.isAfter(holdoutTime)) {
                        throw new IllegalStateException(
                                "Future interaction leaked into training for user " + userId);
                    }
                });
    }

    private long countMissing(List<BookEntity> books, Predicate<BookEntity> predicate) {
        return books.stream().filter(predicate).count();
    }

    private long countSplit(List<Interaction> interactions, Split split) {
        return interactions.stream().filter(interaction -> interaction.split() == split).count();
    }

    private long countCohort(Map<UUID, UserCohort> cohorts, UserCohort cohort) {
        return cohorts.values().stream().filter(value -> value == cohort).count();
    }

    private UserCohort cohortForTrainCount(long trainCount) {
        if (trainCount == 0) {
            return UserCohort.NEW;
        }
        if (trainCount <= 2) {
            return UserCohort.SPARSE;
        }
        return UserCohort.WARM;
    }

    private int safeNonNegative(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : round((double) numerator / denominator);
    }

    private double percent(long numerator, long denominator) {
        return denominator == 0 ? 0.0 : round(((double) numerator / denominator) * 100.0);
    }

    private double sparsity(long uniqueInteractions, long users, long books) {
        if (users == 0 || books == 0) {
            return 0.0;
        }
        return round(1.0 - ((double) uniqueInteractions / ((double) users * books)));
    }

    private double round(double value) {
        return Math.round(value * 1_000_000.0) / 1_000_000.0;
    }

    private record UserBookKey(UUID userId, Long bookId) {}

    private final class PairAccumulator {
        private final UserBookKey key;
        private LocalDateTime firstInteractionAt;
        private LocalDateTime lastInteractionAt;
        private int occurrenceCount;
        private int renewalCount;

        private PairAccumulator(UserBookKey key) {
            this.key = key;
        }

        private PairAccumulator add(LocalDateTime interactionAt, int renewals) {
            if (firstInteractionAt == null || interactionAt.isBefore(firstInteractionAt)) {
                firstInteractionAt = interactionAt;
            }
            if (lastInteractionAt == null || interactionAt.isAfter(lastInteractionAt)) {
                lastInteractionAt = interactionAt;
            }
            occurrenceCount++;
            renewalCount += renewals;
            return this;
        }

        private Interaction toInteraction(Split split, UserCohort cohort) {
            if (split == null) {
                throw new IllegalStateException("Missing dataset split for " + key);
            }
            double preferenceWeight =
                    1.0
                            + (0.15 * Math.min(renewalCount, 2))
                            + (0.10 * Math.min(Math.max(occurrenceCount - 1, 0), 2));
            return new Interaction(
                    key.userId(),
                    key.bookId(),
                    firstInteractionAt,
                    lastInteractionAt,
                    occurrenceCount,
                    renewalCount,
                    round(preferenceWeight),
                    split,
                    cohort);
        }
    }
}
