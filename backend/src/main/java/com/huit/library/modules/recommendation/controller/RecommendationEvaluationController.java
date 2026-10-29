package com.huit.library.modules.recommendation.controller;

import com.huit.library.modules.recommendation.dto.RecommendationEvaluationResponseDTO;
import com.huit.library.modules.recommendation.service.RecommendationEvaluationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/recommendations")
@Tag(name = "Recommendation Evaluation", description = "Offline evaluation for the recommendation module")
public class RecommendationEvaluationController {
    private final RecommendationEvaluationService evaluationService;

    public RecommendationEvaluationController(RecommendationEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    @GetMapping("/evaluation")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Evaluate recommendation baselines and Hybrid on a labeled topic proxy")
    public RecommendationEvaluationResponseDTO evaluate(
            @RequestParam(defaultValue = "10") int k,
            @RequestParam(defaultValue = "40") int scenarios) {
        return evaluationService.evaluate(k, scenarios);
    }
}
