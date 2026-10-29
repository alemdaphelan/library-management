package com.huit.library.modules.rag.service;

import com.huit.library.modules.rag.config.RagProperties;
import com.huit.library.modules.rag.dto.RagChatResponseDTO;
import com.huit.library.modules.rag.dto.RagSourceDTO;
import com.huit.library.modules.rag.model.RagRetrievedContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class RagChatService {
    private static final Logger log = LoggerFactory.getLogger(RagChatService.class);
    private static final Set<String> FALLBACK_STOP_WORDS = Set.of(
            "ai", "bao", "bi", "cac", "cho", "co", "cua", "duoc", "gi", "he", "khi", "khong",
            "la", "lam", "mot", "nao", "nhung", "sach", "sinh", "tai", "the", "thi", "thu", "toi",
            "trong", "tu", "va", "vao", "ve", "vien");

    private final RagProperties properties;
    private final RagInputGuard inputGuard;
    private final RagRateLimiter rateLimiter;
    private final RagRetrievalService retrievalService;
    private final RagSuggestionService suggestionService;
    private final RagPrivacyFilter privacyFilter;
    private final RagKnowledgeStore knowledgeStore;
    private final GeminiApiClient geminiApiClient;

    public RagChatService(
            RagProperties properties,
            RagInputGuard inputGuard,
            RagRateLimiter rateLimiter,
            RagRetrievalService retrievalService,
            RagSuggestionService suggestionService,
            RagPrivacyFilter privacyFilter,
            RagKnowledgeStore knowledgeStore,
            GeminiApiClient geminiApiClient) {
        this.properties = properties;
        this.inputGuard = inputGuard;
        this.rateLimiter = rateLimiter;
        this.retrievalService = retrievalService;
        this.suggestionService = suggestionService;
        this.privacyFilter = privacyFilter;
        this.knowledgeStore = knowledgeStore;
        this.geminiApiClient = geminiApiClient;
    }

    public RagChatResponseDTO ask(String rawQuestion, String actorKey) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Chatbot RAG đang được tắt trong cấu hình");
        }
        String question = inputGuard.validate(rawQuestion);
        rateLimiter.check(actorKey);
        UUID requestId = UUID.randomUUID();
        Instant startedAt = Instant.now();
        List<RagRetrievedContext> contexts = List.of();
        boolean fallbackUsed = false;
        String model = geminiApiClient.isConfigured() ? properties.getGenerationModel() : "extractive-fallback";
        String status = "SUCCESS";
        try {
            contexts = retrievalService.retrieve(question);
            if (contexts.isEmpty()) {
                fallbackUsed = true;
                model = "no-evidence";
                return response(requestId,
                        "Mình chưa tìm thấy nguồn đủ tin cậy để trả lời câu hỏi này. Bạn vui lòng diễn đạt cụ thể hơn hoặc liên hệ thủ thư.",
                        contexts, true, model);
            }

            String answer;
            if (isMoodRecommendation(question) && contexts.stream().anyMatch(context -> "CATALOG".equals(context.type()))) {
                fallbackUsed = true;
                model = "catalog-grounded-recommendation";
                answer = moodRecommendationAnswer(contexts);
            } else if (geminiApiClient.isConfigured()) {
                try {
                    answer = geminiApiClient.generate(privacyFilter.redact(question), contexts);
                    if (!containsSourceCitation(answer, contexts.size())) {
                        throw new IllegalStateException("Gemini response does not cite retrieved evidence");
                    }
                } catch (RuntimeException exception) {
                    log.warn("Gemini generation failed for RAG request {}; using grounded extractive fallback", requestId, exception);
                    fallbackUsed = true;
                    model = "extractive-fallback";
                    answer = extractiveFallback(question, contexts);
                }
            } else {
                fallbackUsed = true;
                answer = extractiveFallback(question, contexts);
            }
            return response(requestId, answer, contexts, fallbackUsed, model);
        } catch (RuntimeException exception) {
            status = "FAILED";
            throw exception;
        } finally {
            try {
                knowledgeStore.audit(
                        requestId,
                        sha256(actorKey),
                        sha256(question),
                        contexts.size(),
                        !contexts.isEmpty(),
                        fallbackUsed,
                        model,
                        status,
                        Duration.between(startedAt, Instant.now()).toMillis());
            } catch (RuntimeException auditException) {
                log.warn("Could not persist RAG audit for request {}", requestId, auditException);
            }
        }
    }

    private RagChatResponseDTO response(
            UUID requestId,
            String answer,
            List<RagRetrievedContext> contexts,
            boolean fallbackUsed,
            String model) {
        List<RagSourceDTO> sources = contexts.stream()
                .map(context -> new RagSourceDTO(
                        context.sourceKey(),
                        context.title(),
                        context.sourceUri(),
                        context.type(),
                        snippet(context.content()),
                        Math.round(context.score() * 10_000.0) / 10_000.0))
                .toList();
        return new RagChatResponseDTO(
                requestId,
                answer,
                sources,
                suggestionService.suggestions(contexts),
                !contexts.isEmpty(),
                fallbackUsed,
                model);
    }

    private String extractiveFallback(String question, List<RagRetrievedContext> contexts) {
        Set<String> questionTerms = meaningfulTerms(question);
        boolean quantityQuestion = asksForQuantity(question);
        List<SentenceCandidate> candidates = new ArrayList<>();
        for (int contextIndex = 0; contextIndex < Math.min(1, contexts.size()); contextIndex++) {
            RagRetrievedContext context = contexts.get(contextIndex);
            String contextWithoutHeading = context.content()
                    .replaceFirst("^#\\s+[^\\r\\n]+(?:\\r?\\n)+", "");
            for (String sentence : contextWithoutHeading.split("(?<=[.!?])\\s+")) {
                String normalizedSentence = sentence.replaceAll("\\s+", " ").trim();
                if (normalizedSentence.isBlank()) {
                    continue;
                }
                Set<String> sentenceTerms = meaningfulTerms(normalizedSentence);
                long overlap = questionTerms.stream().filter(sentenceTerms::contains).count();
                double quantityBonus = quantityQuestion && normalizedSentence.matches(".*\\d.*") ? 50.0 : 0.0;
                double score = overlap * 10.0 + quantityBonus + context.score();
                candidates.add(new SentenceCandidate(contextIndex + 1, normalizedSentence, score, overlap));
            }
        }
        List<SentenceCandidate> ranked = candidates.stream()
                .filter(candidate -> candidate.overlap() > 0)
                .sorted(Comparator.comparingDouble(SentenceCandidate::score).reversed())
                .toList();
        double relativeCutoff = ranked.isEmpty() ? 0.0 : ranked.get(0).score() * 0.5;
        List<SentenceCandidate> selected = ranked.stream()
                .filter(candidate -> candidate.score() >= relativeCutoff)
                .limit(4)
                .toList();
        if (selected.isEmpty()) {
            return basicExtractiveFallback(contexts);
        }

        StringBuilder answer = new StringBuilder("Mình tìm thấy các thông tin liên quan trong kho tri thức:\n\n");
        for (SentenceCandidate candidate : selected) {
            answer.append('[').append(candidate.sourceNumber()).append("] ")
                    .append(candidate.sentence())
                    .append("\n\n");
        }
        return answer.toString().trim();
    }

    private String basicExtractiveFallback(List<RagRetrievedContext> contexts) {
        StringBuilder answer = new StringBuilder("Mình tìm thấy các thông tin liên quan trong kho tri thức:\n\n");
        for (int index = 0; index < Math.min(2, contexts.size()); index++) {
            answer.append('[').append(index + 1).append("] ")
                    .append(snippet(contexts.get(index).content()))
                    .append("\n\n");
        }
        return answer.toString().trim();
    }

    private boolean isMoodRecommendation(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value.toLowerCase(Locale.ROOT),
                        Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd');
        return normalized.contains("buon")
                || normalized.contains("tam trang")
                || normalized.contains("cang thang")
                || normalized.contains("stress")
                || normalized.contains("lo au")
                || normalized.contains("dong luc")
                || normalized.contains("thu gian");
    }

    private String moodRecommendationAnswer(List<RagRetrievedContext> contexts) {
        List<RagRetrievedContext> books = contexts.stream()
                .filter(context -> "CATALOG".equals(context.type()))
                .limit(3)
                .toList();
        StringBuilder answer = new StringBuilder(
                "Mình rất tiếc vì hôm nay bạn đang không vui. Dựa trên những tài liệu hiện có trong thư viện, bạn có thể tham khảo:\n\n");
        for (RagRetrievedContext book : books) {
            int sourceNumber = contexts.indexOf(book) + 1;
            answer.append('[').append(sourceNumber).append("] ")
                    .append(book.title())
                    .append(" — xem thông tin và tình trạng bản sao trước khi mượn.\n\n");
        }
        answer.append("Nếu cảm giác buồn hoặc lo âu kéo dài, bạn cũng nên chia sẻ với người thân hoặc bộ phận hỗ trợ sinh viên của trường.");
        return answer.toString();
    }

    private Set<String> meaningfulTerms(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value.toLowerCase(Locale.ROOT),
                        Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        Set<String> terms = new LinkedHashSet<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 2 && !FALLBACK_STOP_WORDS.contains(token)) {
                terms.add(token);
            }
        }
        return terms;
    }

    private boolean asksForQuantity(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value.toLowerCase(Locale.ROOT),
                        Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9]+", " ");
        return normalized.contains("bao lau")
                || normalized.contains("bao nhieu")
                || normalized.contains("may lan")
                || normalized.contains("thoi han");
    }

    private String snippet(String content) {
        String normalized = content.replaceFirst("^#\\s+[^\\r\\n]+(?:\\r?\\n)+", "")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 497).trim() + "...";
    }

    private boolean containsSourceCitation(String answer, int sourceCount) {
        for (int source = 1; source <= sourceCount; source++) {
            if (answer.contains("[" + source + "]")) {
                return true;
            }
        }
        return false;
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tạo audit hash", exception);
        }
    }

    private record SentenceCandidate(int sourceNumber, String sentence, double score, long overlap) {
    }
}
