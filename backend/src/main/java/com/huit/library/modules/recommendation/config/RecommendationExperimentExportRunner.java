package com.huit.library.modules.recommendation.config;

import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset;
import com.huit.library.modules.recommendation.service.RecommendationExperimentDatasetService;
import com.huit.library.modules.recommendation.service.RecommendationExperimentExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "library.recommendation.experiment.export-enabled",
        havingValue = "true")
public class RecommendationExperimentExportRunner implements ApplicationRunner {

    private final RecommendationExperimentDatasetService datasetService;
    private final RecommendationExperimentExporter exporter;

    @Value("${library.recommendation.experiment.output-dir:target/recommendation-alg1}")
    private String outputDirectory;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        RecommendationExperimentDataset dataset = datasetService.buildDataset();
        Path output = exporter.export(dataset, Path.of(outputDirectory));
        log.info(
                "ALG-1 recommendation dataset exported to {} (train={}, validation={}, test={})",
                output,
                dataset.summary().trainInteractions(),
                dataset.summary().validationInteractions(),
                dataset.summary().testInteractions());
    }
}
