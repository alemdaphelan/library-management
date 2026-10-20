package com.huit.library.modules.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.modules.rag.config.RagProperties;
import com.huit.library.modules.rag.dto.RagChatResponseDTO;
import com.huit.library.modules.rag.dto.RagIndexStatusDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagEvaluationServiceTest {
    @Test
    void loadsControlledDatasetAndCalculatesRefusalMetrics() {
        RagProperties properties = new RagProperties();
        RagChatService chatService = mock(RagChatService.class);
        RagKnowledgeIndexService indexService = mock(RagKnowledgeIndexService.class);
        when(indexService.status()).thenReturn(new RagIndexStatusDTO(
                4, 7, "local-hash-v1:768", false));
        when(chatService.ask(anyString(), anyString())).thenAnswer(invocation -> {
            String question = invocation.getArgument(0, String.class);
            if (question.toLowerCase().contains("bỏ qua mọi chỉ dẫn")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt injection blocked");
            }
            return new RagChatResponseDTO(
                    UUID.randomUUID(),
                    "Mình chưa tìm thấy nguồn đủ tin cậy để trả lời câu hỏi này.",
                    List.of(),
                    List.of(),
                    false,
                    true,
                    "no-evidence");
        });
        RagEvaluationService service = new RagEvaluationService(
                properties, chatService, indexService, new ObjectMapper());

        var report = service.evaluate(0);

        assertThat(report.datasetVersion()).isEqualTo("2026.10.20-v2");
        assertThat(report.totalCases()).isEqualTo(14);
        assertThat(report.answerCases()).isEqualTo(12);
        assertThat(report.refusalCases()).isEqualTo(2);
        assertThat(report.correctRefusalRate()).isEqualTo(1.0);
        assertThat(report.unexpectedErrors()).isZero();
    }
}
