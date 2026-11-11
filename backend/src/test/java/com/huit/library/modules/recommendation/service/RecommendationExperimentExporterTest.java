package com.huit.library.modules.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Interaction;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Split;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Summary;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.UserCohort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationExperimentExporterTest {

    @TempDir Path tempDirectory;

    @Test
    void exportsCompleteAnonymizedAlg1ArtifactSet() throws Exception {
        UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RecommendationExperimentDataset dataset =
                new RecommendationExperimentDataset(
                        RecommendationExperimentDatasetService.DATASET_NAME,
                        Instant.parse("2026-11-04T00:00:00Z"),
                        RecommendationExperimentDatasetService.SPLIT_STRATEGY,
                        new Summary(
                                1, 2, 2, 2, 2.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1, 0, 1, 0,
                                1, 0, 1),
                        List.of(
                                interaction(userId, 1L, "2026-01-01T08:00:00", Split.TRAIN),
                                interaction(userId, 2L, "2026-02-01T08:00:00", Split.TEST)),
                        List.of(),
                        List.of(2L));
        RecommendationExperimentExporter exporter =
                new RecommendationExperimentExporter(new ObjectMapper().findAndRegisterModules());

        Path output = exporter.export(dataset, tempDirectory.resolve("alg1"));

        assertThat(output).isEqualTo(tempDirectory.resolve("alg1").toAbsolutePath().normalize());
        assertThat(output).isDirectoryContaining("glob:**/summary.json");
        assertThat(output).isDirectoryContaining("glob:**/config.json");
        assertThat(output).isDirectoryContaining("glob:**/interactions_clean.csv");
        assertThat(output).isDirectoryContaining("glob:**/train.csv");
        assertThat(output).isDirectoryContaining("glob:**/validation.csv");
        assertThat(output).isDirectoryContaining("glob:**/test.csv");
        assertThat(output).isDirectoryContaining("glob:**/new_users.csv");
        assertThat(output).isDirectoryContaining("glob:**/new_books.csv");
        assertThat(output).isDirectoryContaining("glob:**/data_dictionary.md");
        assertThat(output).isDirectoryContaining("glob:**/README.md");

        assertThat(Files.readAllLines(output.resolve("train.csv"))).hasSize(2);
        assertThat(Files.readAllLines(output.resolve("validation.csv"))).hasSize(1);
        assertThat(Files.readAllLines(output.resolve("test.csv"))).hasSize(2);
        assertThat(Files.readString(output.resolve("new_books.csv"))).contains("book_id", "2");
        assertThat(Files.readString(output.resolve("README.md")))
                .contains("không xáo trộn ngẫu nhiên", "không chứa họ tên");

        String allOutput =
                Files.walk(output)
                        .filter(Files::isRegularFile)
                        .map(
                                path -> {
                                    try {
                                        return Files.readString(path);
                                    } catch (Exception exception) {
                                        throw new IllegalStateException(exception);
                                    }
                                })
                        .reduce("", String::concat);
        assertThat(allOutput)
                .doesNotContain("@gmail.com")
                .doesNotContain("2001230219")
                .doesNotContain("Nguyễn Bảo Hân");
    }

    private Interaction interaction(UUID userId, Long bookId, String time, Split split) {
        LocalDateTime interactionTime = LocalDateTime.parse(time);
        return new Interaction(
                userId,
                bookId,
                interactionTime,
                interactionTime,
                1,
                0,
                1.0,
                split,
                UserCohort.SPARSE);
    }
}
