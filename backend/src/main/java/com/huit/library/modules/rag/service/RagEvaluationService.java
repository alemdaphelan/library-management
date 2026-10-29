package com.huit.library.modules.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.modules.rag.config.RagProperties;
import com.huit.library.modules.rag.dto.RagChatResponseDTO;
import com.huit.library.modules.rag.dto.RagEvaluationCaseResultDTO;
import com.huit.library.modules.rag.dto.RagEvaluationReportDTO;
import com.huit.library.modules.rag.dto.RagIndexStatusDTO;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.Normalizer;

@Service
public class RagEvaluationService {
    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");
    private static final long MAX_DELAY_MILLIS = 10_000L;

    private final RagProperties properties;
    private final RagChatService chatService;
    private final RagKnowledgeIndexService indexService;
    private final ObjectMapper objectMapper;
    private final Resource datasetResource;

    @Autowired
    public RagEvaluationService(
            RagProperties properties,
            RagChatService chatService,
            RagKnowledgeIndexService indexService,
            ObjectMapper objectMapper) {
        this(properties, chatService, indexService, objectMapper,
                new ClassPathResource("rag/evaluation-dataset.json"));
    }

    RagEvaluationService(
            RagProperties properties,
            RagChatService chatService,
            RagKnowledgeIndexService indexService,
            ObjectMapper objectMapper,
            Resource datasetResource) {
        this.properties = properties;
        this.chatService = chatService;
        this.indexService = indexService;
        this.objectMapper = objectMapper;
        this.datasetResource = datasetResource;
    }

    public RagEvaluationReportDTO evaluate(long requestedDelayMillis) {
        EvaluationDataset dataset = loadDataset();
        long delayMillis = Math.max(0L, Math.min(requestedDelayMillis, MAX_DELAY_MILLIS));
        List<RagEvaluationCaseResultDTO> results = new ArrayList<>();
        for (int index = 0; index < dataset.cases().size(); index++) {
            EvaluationCase testCase = dataset.cases().get(index);
            results.add(evaluateCase(testCase));
            if (delayMillis > 0 && index < dataset.cases().size() - 1) {
                sleep(delayMillis);
            }
        }
        return summarize(dataset, results);
    }

    private RagEvaluationCaseResultDTO evaluateCase(EvaluationCase testCase) {
        long startedAt = System.nanoTime();
        RagChatResponseDTO response = null;
        String status = "SUCCESS";
        String answer = null;
        boolean refused = false;
        try {
            response = askWithTransientRetry(testCase);
            answer = response.answer();
            refused = isRefusal(response);
        } catch (ResponseStatusException exception) {
            boolean guardRejected = exception.getStatusCode() == HttpStatus.BAD_REQUEST;
            refused = guardRejected;
            status = testCase.blockedByGuardExpected() && guardRejected
                    ? "EXPECTED_GUARD_REJECTION"
                    : "UNEXPECTED_HTTP_" + exception.getStatusCode().value();
            answer = exception.getReason();
        } catch (RuntimeException exception) {
            status = "ERROR_" + exception.getClass().getSimpleName();
            answer = "Evaluation error: " + safeMessage(exception);
        }

        List<String> retrievedKeys = response == null ? List.of() : response.sources().stream()
                .map(source -> source.sourceKey())
                .distinct()
                .toList();
        Set<String> retrievedSet = new HashSet<>(retrievedKeys);
        long matched = testCase.expectedSourceKeys().stream()
                .filter(expected -> retrievedSet.stream().anyMatch(actual -> sourceMatches(expected, actual)))
                .count();
        boolean retrievalHit = !testCase.expectedSourceKeys().isEmpty() && matched > 0;
        double recall = testCase.expectedSourceKeys().isEmpty()
                ? 0.0
                : (double) matched / testCase.expectedSourceKeys().size();
        boolean citationValid = response != null && hasValidCitation(response.answer(), response.sources().size());
        boolean answerContentCorrect = testCase.answerExpected()
                && containsExpectedTerms(answer, testCase.expectedAnswerTerms());
        double topRetrievalScore = response == null ? 0.0 : response.sources().stream()
                .mapToDouble(source -> source.score())
                .max()
                .orElse(0.0);
        long latencyMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);

        return new RagEvaluationCaseResultDTO(
                testCase.id(),
                testCase.category(),
                testCase.question(),
                List.copyOf(testCase.expectedSourceKeys()),
                retrievedKeys,
                testCase.answerExpected(),
                refused,
                retrievalHit,
                round(recall),
                testCase.answerExpected() && retrievalHit,
                answerContentCorrect,
                citationValid,
                response != null && response.grounded(),
                response != null && response.fallbackUsed(),
                round(topRetrievalScore),
                response == null ? null : response.model(),
                latencyMs,
                status,
                answer);
    }

    private RagEvaluationReportDTO summarize(
            EvaluationDataset dataset,
            List<RagEvaluationCaseResultDTO> results) {
        RagIndexStatusDTO indexStatus = indexService.status();
        List<RagEvaluationCaseResultDTO> answerCases = results.stream()
                .filter(RagEvaluationCaseResultDTO::answerExpected)
                .toList();
        List<RagEvaluationCaseResultDTO> refusalCases = results.stream()
                .filter(result -> !result.answerExpected())
                .toList();
        int retrievalHits = (int) answerCases.stream().filter(RagEvaluationCaseResultDTO::retrievalHit).count();
        double recallSum = answerCases.stream()
                .mapToDouble(RagEvaluationCaseResultDTO::retrievalRecallAtK)
                .sum();
        int sourceCorrect = (int) answerCases.stream().filter(RagEvaluationCaseResultDTO::sourceCorrect).count();
        int groundedAnswers = (int) answerCases.stream()
                .filter(RagEvaluationCaseResultDTO::answerContentCorrect)
                .count();
        int citations = (int) answerCases.stream().filter(RagEvaluationCaseResultDTO::citationValid).count();
        int refusalsCorrect = (int) refusalCases.stream().filter(RagEvaluationCaseResultDTO::refused).count();
        int fallbacks = (int) results.stream().filter(RagEvaluationCaseResultDTO::fallbackUsed).count();
        int unexpectedErrors = (int) results.stream()
                .filter(result -> result.status().startsWith("ERROR_")
                        || result.status().startsWith("UNEXPECTED_"))
                .count();
        List<Long> latencies = results.stream()
                .map(RagEvaluationCaseResultDTO::latencyMs)
                .sorted()
                .toList();

        return new RagEvaluationReportDTO(
                Instant.now(),
                dataset.version(),
                dataset.description(),
                properties.getGenerationModel(),
                indexStatus.embeddingProvider(),
                indexStatus.geminiConfigured(),
                Math.max(1, Math.min(properties.getTopK(), 10)),
                results.size(),
                answerCases.size(),
                refusalCases.size(),
                unexpectedErrors,
                ratio(retrievalHits, answerCases.size()),
                answerCases.isEmpty() ? 0.0 : round(recallSum / answerCases.size()),
                ratio(sourceCorrect, answerCases.size()),
                ratio(groundedAnswers, answerCases.size()),
                ratio(citations, answerCases.size()),
                ratio(refusalsCorrect, refusalCases.size()),
                ratio(fallbacks, results.size()),
                percentile(latencies, 0.50),
                percentile(latencies, 0.95),
                List.copyOf(results));
    }

    private EvaluationDataset loadDataset() {
        try (var input = datasetResource.getInputStream()) {
            EvaluationDataset dataset = objectMapper.readValue(input, EvaluationDataset.class);
            if (dataset.cases() == null || dataset.cases().isEmpty()) {
                throw new IllegalStateException("Bộ câu hỏi RAG không có dữ liệu");
            }
            return dataset;
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể đọc bộ câu hỏi đánh giá RAG", exception);
        }
    }

    private boolean hasValidCitation(String answer, int sourceCount) {
        if (answer == null || sourceCount == 0) {
            return false;
        }
        Matcher matcher = CITATION.matcher(answer);
        while (matcher.find()) {
            int citation = Integer.parseInt(matcher.group(1));
            if (citation >= 1 && citation <= sourceCount) {
                return true;
            }
        }
        return false;
    }

    private boolean sourceMatches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return expected.endsWith("*")
                ? actual.startsWith(expected.substring(0, expected.length() - 1))
                : actual.equals(expected);
    }

    private boolean containsExpectedTerms(String answer, List<String> expectedTerms) {
        if (answer == null || expectedTerms == null || expectedTerms.isEmpty()) {
            return false;
        }
        String normalizedAnswer = normalize(answer);
        return expectedTerms.stream()
                .allMatch(termGroup -> java.util.Arrays.stream(termGroup.split("\\|"))
                        .map(this::normalize)
                        .anyMatch(normalizedAnswer::contains));
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9_]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean isRefusal(RagChatResponseDTO response) {
        if (!response.grounded()) {
            return true;
        }
        String answer = response.answer() == null ? "" : response.answer().toLowerCase(Locale.ROOT);
        return answer.contains("chưa tìm thấy nguồn")
                || answer.contains("chưa đủ thông tin")
                || answer.contains("không có thông tin trong nguồn")
                || answer.contains("không thể trả lời dựa trên nguồn");
    }

    private void sleep(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Đánh giá RAG bị gián đoạn", exception);
        }
    }

    private RagChatResponseDTO askWithTransientRetry(EvaluationCase testCase) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return chatService.ask(testCase.question(), "rag-evaluation:" + testCase.id());
            } catch (HttpClientErrorException.TooManyRequests exception) {
                if (attempt == 3) {
                    throw exception;
                }
                sleep(5_000L * attempt);
            }
        }
        throw new IllegalStateException("Không thể hoàn tất ca đánh giá RAG");
    }

    private long percentile(List<Long> sortedValues, double percentile) {
        if (sortedValues.isEmpty()) {
            return 0L;
        }
        int index = (int) Math.ceil(percentile * sortedValues.size()) - 1;
        return sortedValues.get(Math.max(0, Math.min(index, sortedValues.size() - 1)));
    }

    private double ratio(int numerator, int denominator) {
        return denominator == 0 ? 0.0 : round((double) numerator / denominator);
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private record EvaluationDataset(
            String version,
            String description,
            List<EvaluationCase> cases) {
    }

    private record EvaluationCase(
            String id,
            String category,
            String question,
            List<String> expectedSourceKeys,
            List<String> expectedAnswerTerms,
            boolean answerExpected,
            boolean blockedByGuardExpected) {
    }
}
