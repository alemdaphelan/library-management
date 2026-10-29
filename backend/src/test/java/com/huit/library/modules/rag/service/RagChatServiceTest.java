package com.huit.library.modules.rag.service;

import com.huit.library.modules.rag.config.RagProperties;
import com.huit.library.modules.rag.model.RagRetrievedContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RagChatServiceTest {
    private RagRetrievalService retrievalService;
    private RagKnowledgeStore knowledgeStore;
    private GeminiApiClient geminiApiClient;
    private RagChatService service;

    @BeforeEach
    void setUp() {
        RagProperties properties = new RagProperties();
        RagInputGuard inputGuard = new RagInputGuard(properties);
        RagRateLimiter rateLimiter = mock(RagRateLimiter.class);
        retrievalService = mock(RagRetrievalService.class);
        knowledgeStore = mock(RagKnowledgeStore.class);
        geminiApiClient = mock(GeminiApiClient.class);
        when(geminiApiClient.isConfigured()).thenReturn(false);
        service = new RagChatService(
                properties,
                inputGuard,
                rateLimiter,
                retrievalService,
                new RagSuggestionService(),
                new RagPrivacyFilter(),
                knowledgeStore,
                geminiApiClient);
    }

    @Test
    void returnsGroundedExtractiveFallbackWithSourcesWhenGeminiIsNotConfigured() {
        when(retrievalService.retrieve(anyString())).thenReturn(List.of(new RagRetrievedContext(
                "circulation-policy",
                "Quy định mượn, trả, gia hạn và đặt giữ",
                "classpath:rag/circulation-policy.md",
                "POLICY",
                "Mỗi tài liệu được gia hạn tối đa một lần.",
                0.91)));

        var response = service.ask("Tôi được gia hạn mấy lần?", "anonymous:test");

        assertThat(response.grounded()).isTrue();
        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.answer()).contains("gia hạn tối đa một lần");
        assertThat(response.sources()).hasSize(1);
        verify(knowledgeStore).audit(any(), anyString(), anyString(), anyInt(), anyBoolean(),
                anyBoolean(), anyString(), anyString(), anyLong());
    }

    @Test
    void refusesToInventWhenRetrievalHasNoEvidence() {
        when(retrievalService.retrieve(anyString())).thenReturn(List.of());

        var response = service.ask("Một câu hỏi không có trong dữ liệu", "anonymous:test");

        assertThat(response.grounded()).isFalse();
        assertThat(response.answer()).contains("chưa tìm thấy nguồn đủ tin cậy");
        assertThat(response.model()).isEqualTo("no-evidence");
    }

    @Test
    void fallsBackWhenGeminiAnswerDoesNotCiteRetrievedEvidence() {
        when(geminiApiClient.isConfigured()).thenReturn(true);
        when(geminiApiClient.generate(anyString(), any())).thenReturn("Câu trả lời không có dẫn nguồn.");
        when(retrievalService.retrieve(anyString())).thenReturn(List.of(new RagRetrievedContext(
                "circulation-policy",
                "Quy định mượn, trả, gia hạn và đặt giữ",
                "classpath:rag/circulation-policy.md",
                "POLICY",
                "Mỗi tài liệu được gia hạn tối đa một lần.",
                0.91)));

        var response = service.ask("Tôi được gia hạn mấy lần?", "anonymous:test");

        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.model()).isEqualTo("extractive-fallback");
        assertThat(response.answer()).contains("gia hạn tối đa một lần");
    }

    @Test
    void fallsBackWhenGeminiTimesOut() {
        when(geminiApiClient.isConfigured()).thenReturn(true);
        when(geminiApiClient.generate(anyString(), any())).thenThrow(new RuntimeException("read timeout"));
        when(retrievalService.retrieve(anyString())).thenReturn(List.of(new RagRetrievedContext(
                "circulation-policy",
                "Quy định mượn, trả, gia hạn và đặt giữ",
                "classpath:rag/circulation-policy.md",
                "POLICY",
                "Mỗi tài liệu được gia hạn tối đa một lần.",
                0.91)));

        var response = service.ask("Tôi được gia hạn mấy lần?", "anonymous:test");

        assertThat(response.fallbackUsed()).isTrue();
        assertThat(response.answer()).contains("gia hạn tối đa một lần");
    }

    @Test
    void recordsFailedAuditWhenVectorRetrievalFails() {
        when(retrievalService.retrieve(anyString())).thenThrow(new RuntimeException("vector store unavailable"));

        assertThatThrownBy(() -> service.ask("Quy định gia hạn?", "anonymous:test"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("vector store unavailable");

        verify(knowledgeStore).audit(any(), anyString(), anyString(), anyInt(), anyBoolean(),
                anyBoolean(), anyString(), org.mockito.ArgumentMatchers.eq("FAILED"), anyLong());
    }

    @Test
    void extractiveFallbackSelectsSentenceRelevantToQuestion() {
        when(retrievalService.retrieve(anyString())).thenReturn(List.of(new RagRetrievedContext(
                "circulation-policy",
                "Quy định mượn, trả, gia hạn và đặt giữ",
                "classpath:rag/circulation-policy.md",
                "POLICY",
                "Sinh viên phải giữ gìn tài liệu. Lượt giữ sách có hiệu lực 48 giờ kể từ khi được thông báo. "
                        + "Nếu hết hạn khi thư viện đóng cửa thì kéo dài đến thời điểm mở cửa gần nhất.",
                0.82)));

        var response = service.ask(
                "Sách được giữ bao lâu nếu hết hạn khi thư viện đóng cửa?",
                "anonymous:test");

        assertThat(response.answer()).contains("48 giờ", "mở cửa gần nhất");
        assertThat(response.answer()).doesNotContain("Sinh viên phải giữ gìn tài liệu");
    }

    @Test
    void moodQuestionReturnsOnlyGroundedCatalogRecommendations() {
        when(retrievalService.retrieve(anyString())).thenReturn(List.of(new RagRetrievedContext(
                "catalog-book-42",
                "Hạt giống tâm hồn",
                "/book/42",
                "CATALOG",
                "Tên sách: Hạt giống tâm hồn; thể loại: Kỹ năng sống.",
                0.94)));

        var response = service.ask(
                "Hôm nay tôi buồn. Tôi muốn tìm một cuốn sách để tâm trạng tốt hơn.",
                "anonymous:test");

        assertThat(response.grounded()).isTrue();
        assertThat(response.model()).isEqualTo("catalog-grounded-recommendation");
        assertThat(response.answer()).contains("Hạt giống tâm hồn", "[1]");
        assertThat(response.sources()).extracting(source -> source.sourceUri()).containsExactly("/book/42");
    }
}
