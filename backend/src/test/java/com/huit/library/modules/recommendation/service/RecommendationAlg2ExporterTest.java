package com.huit.library.modules.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.modules.recommendation.dto.RecommendationAlg2Report;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationAlg2ExporterTest {

    @TempDir
    Path tempDirectory;

    @Test
    void exportsMetricsConfigurationSamplesAndReadmeWithoutPersonalIdentifiers() throws Exception {
        RecommendationAlg2Report report = report();

        Path output = new RecommendationAlg2Exporter(new ObjectMapper().findAndRegisterModules())
                .export(report, tempDirectory);

        assertThat(output).isEqualTo(tempDirectory.toAbsolutePath().normalize());
        assertThat(output).isDirectoryContaining(path -> path.getFileName().toString().equals("alg2_report.json"));
        assertThat(output).isDirectoryContaining(path -> path.getFileName().toString().equals("alg2_config.json"));
        assertThat(output).isDirectoryContaining(path -> path.getFileName().toString().equals("alg2_metrics.csv"));
        assertThat(output).isDirectoryContaining(path -> path.getFileName().toString().equals("alg2_sample_top_k.csv"));
        assertThat(output).isDirectoryContaining(path -> path.getFileName().toString().equals("README.md"));

        String allFiles;
        try (var files = Files.walk(output)) {
            allFiles = files.filter(Files::isRegularFile)
                    .map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .reduce("", String::concat);
        }
        assertThat(allFiles)
                .contains("POPULARITY", "CONTENT_TFIDF", "EVAL_USER_01")
                .doesNotContain("student@", "200123", "00000000-0000-0000-0000-000000000001");
    }

    private RecommendationAlg2Report report() {
        RecommendationAlg2Report.MethodMetrics popularity = new RecommendationAlg2Report.MethodMetrics(
                "POPULARITY", 0.1, 1.0, 1.0, 0.2, 1.5, 2.0, 1);
        RecommendationAlg2Report.MethodMetrics content = new RecommendationAlg2Report.MethodMetrics(
                "CONTENT_TFIDF", 0.1, 1.0, 1.0, 0.4, 2.5, 3.0, 1);
        RecommendationAlg2Report.SampleRecommendation sample = new RecommendationAlg2Report.SampleRecommendation(
                "EVAL_USER_01", "SPARSE", "CONTENT_TFIDF", false, null,
                List.of(new RecommendationAlg2Report.Item(1, 2L, "Java nâng cao", 0.9)));
        return new RecommendationAlg2Report(
                "alg1-real", Instant.parse("2026-01-01T00:00:00Z"), 10,
                RecommendationAlg2EvaluationService.CANDIDATE_RULE,
                new RecommendationAlg2Report.DatasetWarning(1, 1, 1,
                        "Very small evaluation cohort; metrics are illustrative and not statistically conclusive."),
                List.of(popularity, content), List.of(sample),
                new RecommendationAlg2Report.Diagnostics(4, 1, 1, 1, 1, 1, 1, true));
    }
}
