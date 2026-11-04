package com.huit.library.modules.recommendation.experiment;

import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Interaction;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Split;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.UserCohort;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecommendationAlg2BaselineModelTest {

    private static final UUID WARM_USER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID COLD_USER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void popularityUsesOnlyTrainAndBreaksTiesByBookId() {
        PopularityBaselineModel model = new PopularityBaselineModel(catalog(), List.of(
                interaction(WARM_USER, 1L, Split.TRAIN, 2.0),
                interaction(WARM_USER, 2L, Split.VALIDATION, 100.0),
                interaction(WARM_USER, 3L, Split.TEST, 100.0)));

        Alg2RecommendationModel.RecommendationResult result = model.recommend(WARM_USER, 3, Set.of());

        assertThat(result.items()).extracting(Alg2RecommendationModel.RecommendationItem::bookId)
                .containsExactly(1L, 2L, 3L);
        assertThat(result.items().get(0).score()).isEqualTo(1.0);
        assertThat(result.items().get(1).score()).isZero();
        assertThat(result.items().get(2).score()).isZero();
    }

    @Test
    void popularityHonorsExclusionsAndRejectsInvalidK() {
        PopularityBaselineModel model = new PopularityBaselineModel(
                catalog(), List.of(interaction(WARM_USER, 1L, Split.TRAIN, 1.0)));

        assertThat(model.recommend(WARM_USER, 2, Set.of(1L)).items())
                .extracting(Alg2RecommendationModel.RecommendationItem::bookId)
                .containsExactly(2L, 3L);
        assertThatThrownBy(() -> model.recommend(WARM_USER, 0, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void contentRanksSimilarNewBookAndDoesNotUseValidationOrTestInProfile() {
        PopularityBaselineModel popularity = new PopularityBaselineModel(catalog(), List.of(
                interaction(WARM_USER, 1L, Split.TRAIN, 1.0)));
        ContentTfIdfBaselineModel content = new ContentTfIdfBaselineModel(catalog(), List.of(
                interaction(WARM_USER, 1L, Split.TRAIN, 1.0),
                interaction(WARM_USER, 3L, Split.VALIDATION, 100.0),
                interaction(WARM_USER, 3L, Split.TEST, 100.0)), popularity);

        Alg2RecommendationModel.RecommendationResult result = content.recommend(WARM_USER, 2, Set.of(1L));

        assertThat(result.fallback()).isFalse();
        assertThat(result.items()).extracting(Alg2RecommendationModel.RecommendationItem::bookId)
                .containsExactly(2L, 3L);
        assertThat(result.items().get(0).score()).isPositive();
        assertThat(content.canScoreBook(2L)).isTrue();
    }

    @Test
    void contentFallsBackToPopularityForColdUserAndRejectsInvalidK() {
        List<Interaction> interactions = List.of(interaction(WARM_USER, 1L, Split.TRAIN, 1.0));
        PopularityBaselineModel popularity = new PopularityBaselineModel(catalog(), interactions);
        ContentTfIdfBaselineModel content = new ContentTfIdfBaselineModel(catalog(), interactions, popularity);

        Alg2RecommendationModel.RecommendationResult result = content.recommend(COLD_USER, 2, Set.of());

        assertThat(result.fallback()).isTrue();
        assertThat(result.fallbackReason()).contains("No TRAIN history");
        assertThat(result.items()).extracting(Alg2RecommendationModel.RecommendationItem::bookId)
                .containsExactly(1L, 2L);
        assertThatThrownBy(() -> content.recommend(WARM_USER, 0, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private List<Alg2CatalogBook> catalog() {
        return List.of(
                new Alg2CatalogBook(1L, "Java căn bản", "Nguyễn A", "HUIT", "CNTT",
                        "Lập trình Java hướng đối tượng", List.of("Lập trình")),
                new Alg2CatalogBook(2L, "Java nâng cao", "Nguyễn B", "HUIT", "CNTT",
                        "Lập trình Java Spring", List.of("Lập trình")),
                new Alg2CatalogBook(3L, "Hóa sinh thực phẩm", "Trần C", "HUIT", "Thực phẩm",
                        "Vi sinh và hóa sinh", List.of("Công nghệ thực phẩm")));
    }

    private Interaction interaction(UUID userId, Long bookId, Split split, double weight) {
        LocalDateTime occurredAt = LocalDateTime.of(2026, 1, 1, 8, 0);
        return new Interaction(userId, bookId, occurredAt, occurredAt, 1, 0, weight, split, UserCohort.SPARSE);
    }
}
