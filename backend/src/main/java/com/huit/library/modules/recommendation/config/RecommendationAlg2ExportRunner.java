package com.huit.library.modules.recommendation.config;

import com.huit.library.modules.recommendation.dto.RecommendationAlg2Report;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset;
import com.huit.library.modules.recommendation.service.RecommendationAlg2EvaluationService;
import com.huit.library.modules.recommendation.service.RecommendationAlg2Exporter;
import com.huit.library.modules.recommendation.service.RecommendationExperimentDatasetService;
import com.huit.library.modules.recommendation.service.RecommendationExperimentExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "library.recommendation.experiment.alg2.export-enabled",
        havingValue = "true")
public class RecommendationAlg2ExportRunner implements ApplicationRunner {

    private final RecommendationExperimentDatasetService datasetService;
    private final RecommendationExperimentExporter experimentExporter;
    private final RecommendationAlg2EvaluationService evaluationService;
    private final RecommendationAlg2Exporter exporter;

    @Value("${library.recommendation.experiment.output-dir:target/recommendation-alg1}")
    private String alg1OutputDirectory;

    @Value("${library.recommendation.experiment.alg2.output-dir:target/recommendation-alg2}")
    private String outputDirectory;

    @Value("${library.recommendation.experiment.alg2.top-k:10}")
    private int topK;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        RecommendationExperimentDataset dataset = datasetService.buildDataset();
        Path alg1Output = experimentExporter.export(dataset, Path.of(alg1OutputDirectory));
        RecommendationAlg2Report report = evaluationService.evaluate(dataset, topK);
        Path alg2Output = exporter.export(report, Path.of(outputDirectory));
        log.info(
                "ALG-1 snapshot refreshed at {} and ALG-2 baseline report exported to {} "
                        + "(train={}, validation={}, test={}, topK={}, evaluationUsers={})",
                alg1Output,
                alg2Output,
                dataset.summary().trainInteractions(),
                dataset.summary().validationInteractions(),
                dataset.summary().testInteractions(),
                report.topK(),
                report.warning().evaluationUsers());
    }
}
