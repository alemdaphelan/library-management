package com.huit.library.modules.rag.controller;

import com.huit.library.modules.rag.dto.RagIndexStatusDTO;
import com.huit.library.modules.rag.dto.RagEvaluationReportDTO;
import com.huit.library.modules.rag.service.RagEvaluationService;
import com.huit.library.modules.rag.service.RagKnowledgeIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/rag")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "RAG Administration", description = "Knowledge index operations")
public class RagAdminController {
    private final RagKnowledgeIndexService indexService;
    private final RagEvaluationService evaluationService;

    public RagAdminController(RagKnowledgeIndexService indexService, RagEvaluationService evaluationService) {
        this.indexService = indexService;
        this.evaluationService = evaluationService;
    }

    @GetMapping("/status")
    @Operation(summary = "Get the active RAG index status")
    public RagIndexStatusDTO status() {
        return indexService.status();
    }

    @PostMapping("/reindex")
    @Operation(summary = "Rebuild the controlled RAG knowledge index")
    public RagIndexStatusDTO reindex() {
        return indexService.rebuild(true);
    }

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate RAG retrieval, grounding, refusal and latency on the controlled dataset")
    public RagEvaluationReportDTO evaluate(
            @RequestParam(defaultValue = "0") long delayMillis) {
        return evaluationService.evaluate(delayMillis);
    }
}
