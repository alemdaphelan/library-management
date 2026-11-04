package com.huit.library.modules.recommendation.config;

import com.huit.library.modules.recommendation.dto.RecommendationAlg2Report;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset;
import com.huit.library.modules.recommendation.service.RecommendationAlg2EvaluationService;
import com.huit.library.modules.recommendation.service.RecommendationAlg2Exporter;
import com.huit.library.modules.recommendation.service.RecommendationExperimentDatasetService;
import com.huit.library.modules.recommendation.service.RecommendationExperimentExporter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecommendationAlg2ExportRunnerTest {

    @Mock
    private RecommendationExperimentDatasetService datasetService;

    @Mock
    private RecommendationExperimentExporter experimentExporter;

    @Mock
    private RecommendationAlg2EvaluationService evaluationService;

    @Mock
    private RecommendationAlg2Exporter alg2Exporter;

    @Mock
    private ApplicationArguments arguments;

    @Test
    void exportsAlg1AndEvaluatesAlg2FromTheSameDatasetSnapshot() throws Exception {
        RecommendationExperimentDataset dataset = dataset();
        RecommendationAlg2Report report = report();
        Path alg1Directory = Path.of("target/test-alg1");
        Path alg2Directory = Path.of("target/test-alg2");

        RecommendationAlg2ExportRunner runner = new RecommendationAlg2ExportRunner(
                datasetService,
                experimentExporter,
                evaluationService,
                alg2Exporter);
        ReflectionTestUtils.setField(runner, "alg1OutputDirectory", alg1Directory.toString());
        ReflectionTestUtils.setField(runner, "outputDirectory", alg2Directory.toString());
        ReflectionTestUtils.setField(runner, "topK", 10);

        when(datasetService.buildDataset()).thenReturn(dataset);
        when(experimentExporter.export(dataset, alg1Directory)).thenReturn(alg1Directory);
        when(evaluationService.evaluate(dataset, 10)).thenReturn(report);
        when(alg2Exporter.export(report, alg2Directory)).thenReturn(alg2Directory);

        runner.run(arguments);

        InOrder order = inOrder(datasetService, experimentExporter, evaluationService, alg2Exporter);
        order.verify(datasetService).buildDataset();
        order.verify(experimentExporter).export(eq(dataset), eq(alg1Directory));
        order.verify(evaluationService).evaluate(eq(dataset), eq(10));
        order.verify(alg2Exporter).export(eq(report), eq(alg2Directory));
        order.verifyNoMoreInteractions();
    }

    private RecommendationExperimentDataset dataset() {
        RecommendationExperimentDataset.Summary summary = new RecommendationExperimentDataset.Summary(
                2, 4, 4, 4, 2.0, 1.0,
                0.0, 25.0, 0.0, 0.0, 0.0, 0.0,
                0.5, 2, 1, 1, 1, 1, 0, 1);
        return new RecommendationExperimentDataset(
                "alg-1-test",
                Instant.parse("2026-11-04T00:00:00Z"),
                "leave-last-two-out",
                summary,
                List.of(),
                List.of(),
                List.of());
    }

    private RecommendationAlg2Report report() {
        return new RecommendationAlg2Report(
                "alg-1-test",
                Instant.parse("2026-11-04T00:00:00Z"),
                10,
                "exclude TRAIN and VALIDATION",
                new RecommendationAlg2Report.DatasetWarning(1, 2, 1, "small cohort"),
                List.of(),
                List.of(),
                new RecommendationAlg2Report.Diagnostics(4, 2, 1, 1, 2, 1, 1, true));
    }
}
