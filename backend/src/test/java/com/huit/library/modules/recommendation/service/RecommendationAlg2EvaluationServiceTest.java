package com.huit.library.modules.recommendation.service;

import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.recommendation.dto.RecommendationAlg2Report;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Interaction;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Split;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.UserCohort;
import com.huit.library.modules.recommendation.experiment.Alg2CatalogBook;
import com.huit.library.modules.recommendation.repository.BookSubjectAssignmentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class RecommendationAlg2EvaluationServiceTest {

    @Test
    void evaluatesBothBaselinesWithOneCandidateRuleAndTestOnlyRelevance() {
        UUID user = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RecommendationExperimentDataset dataset = dataset(user);
        List<Alg2CatalogBook> catalog =
                List.of(
                        book(1L, "Java căn bản", "Lập trình Java"),
                        book(2L, "Java nâng cao", "Java Spring"),
                        book(3L, "Mạng máy tính", "Mạng và bảo mật"),
                        book(4L, "Hóa sinh", "Công nghệ thực phẩm"));
        RecommendationAlg2EvaluationService service = service();

        RecommendationAlg2Report report = service.evaluate(dataset, catalog, 2);

        assertThat(report.metrics())
                .extracting(RecommendationAlg2Report.MethodMetrics::method)
                .containsExactly("POPULARITY", "CONTENT_TFIDF");
        assertThat(report.metrics())
                .allSatisfy(
                        metric -> {
                            assertThat(metric.evaluatedUsers()).isEqualTo(1);
                            assertThat(metric.precisionAtK()).isBetween(0.0, 1.0);
                            assertThat(metric.recallAtK()).isBetween(0.0, 1.0);
                            assertThat(metric.hitRateAtK()).isBetween(0.0, 1.0);
                            assertThat(metric.catalogCoverage()).isBetween(0.0, 1.0);
                        });
        assertThat(report.candidateRule())
                .isEqualTo(RecommendationAlg2EvaluationService.CANDIDATE_RULE);
        assertThat(report.warning().evaluationUsers()).isEqualTo(1);
        assertThat(report.warning().message()).contains("not statistically conclusive");
        assertThat(report.diagnostics().trainingUsesOnlyTrainSplit()).isTrue();
        assertThat(report.samples())
                .extracting(RecommendationAlg2Report.SampleRecommendation::sampleLabel)
                .contains("EVAL_USER_01", "COLD_USER_01");
    }

    @Test
    void rejectsInvalidTopK() {
        assertThatThrownBy(
                        () ->
                                service()
                                        .evaluate(
                                                dataset(UUID.randomUUID()),
                                                List.of(book(1L, "A", "B")),
                                                0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RecommendationAlg2EvaluationService service() {
        return new RecommendationAlg2EvaluationService(
                mock(RecommendationExperimentDatasetService.class),
                mock(BookRepository.class),
                mock(BookSubjectAssignmentRepository.class));
    }

    private RecommendationExperimentDataset dataset(UUID user) {
        LocalDateTime time = LocalDateTime.of(2026, 1, 1, 8, 0);
        List<Interaction> interactions =
                List.of(
                        new Interaction(
                                user, 1L, time, time, 1, 0, 1.0, Split.TRAIN, UserCohort.SPARSE),
                        new Interaction(
                                user,
                                3L,
                                time.plusDays(1),
                                time.plusDays(1),
                                1,
                                0,
                                1.0,
                                Split.VALIDATION,
                                UserCohort.SPARSE),
                        new Interaction(
                                user,
                                2L,
                                time.plusDays(2),
                                time.plusDays(2),
                                1,
                                0,
                                1.0,
                                Split.TEST,
                                UserCohort.SPARSE));
        RecommendationExperimentDataset.Summary summary =
                new RecommendationExperimentDataset.Summary(
                        2, 4, 3, 3, 1.5, 0.75, 50.0, 25.0, 0.0, 0.0, 0.0, 0.0, 0.625, 1, 1, 1, 0, 1,
                        1, 1);
        return new RecommendationExperimentDataset(
                "alg2-test",
                Instant.parse("2026-01-01T00:00:00Z"),
                "temporal leave-last-two-out",
                summary,
                interactions,
                List.of(UUID.fromString("00000000-0000-0000-0000-000000000002")),
                List.of(4L));
    }

    private Alg2CatalogBook book(Long id, String title, String description) {
        return new Alg2CatalogBook(
                id, title, "Tác giả", "HUIT", "CNTT", description, List.of("Lập trình"));
    }
}
