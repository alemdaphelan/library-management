package com.huit.library.modules.recommendation.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Reproducible offline dataset used by ALG-1 and the later algorithm comparisons.
 * It intentionally contains identifiers instead of student personal information.
 */
public record RecommendationExperimentDataset(
        String datasetName,
        Instant generatedAt,
        String splitStrategy,
        Summary summary,
        List<Interaction> interactions,
        List<UUID> newUserIds,
        List<Long> newBookIds) {

    public record Summary(
            long totalStudents,
            long totalEligibleBooks,
            long validRawInteractions,
            long uniqueUserBookInteractions,
            double meanInteractionsPerUser,
            double meanInteractionsPerBook,
            double studentsWithoutHistoryPercent,
            double booksNeverBorrowedPercent,
            double missingCategoryPercent,
            double missingAuthorPercent,
            double missingDepartmentPercent,
            double missingDescriptionPercent,
            double sparsity,
            long trainInteractions,
            long validationInteractions,
            long testInteractions,
            long warmUsers,
            long sparseUsers,
            long newUsers,
            long newBooks) {}

    public record Interaction(
            UUID userId,
            Long bookId,
            LocalDateTime firstInteractionAt,
            LocalDateTime lastInteractionAt,
            int occurrenceCount,
            int renewalCount,
            double preferenceWeight,
            Split split,
            UserCohort cohort) {}

    public enum Split {
        TRAIN,
        VALIDATION,
        TEST
    }

    public enum UserCohort {
        NEW,
        SPARSE,
        WARM
    }
}
