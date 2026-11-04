package com.huit.library.modules.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.modules.recommendation.dto.RecommendationAlg2Report;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

/** Exports anonymized ALG-2 baseline metrics and sample recommendations. */
@Service
@RequiredArgsConstructor
public class RecommendationAlg2Exporter {

    private final ObjectMapper objectMapper;

    public Path export(RecommendationAlg2Report report, Path outputDirectory) throws IOException {
        Path output = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(output);

        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(output.resolve("alg2_report.json").toFile(), report);
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(output.resolve("alg2_config.json").toFile(), new Alg2Configuration(
                        report.datasetName(), report.topK(), report.candidateRule(),
                        "TRAIN only", "TEST only", true));
        writeMetrics(output.resolve("alg2_metrics.csv"), report);
        writeSamples(output.resolve("alg2_sample_top_k.csv"), report);
        writeReadme(output.resolve("README.md"), report);
        return output;
    }

    private void writeMetrics(Path path, RecommendationAlg2Report report) throws IOException {
        try (BufferedWriter writer = writer(path)) {
            writer.write("method,precision_at_k,recall_at_k,hit_rate_at_k,catalog_coverage,average_latency_ms,p95_latency_ms,evaluated_users");
            writer.newLine();
            for (RecommendationAlg2Report.MethodMetrics metric : report.metrics()) {
                writer.write(String.join(",",
                        csv(metric.method()), decimal(metric.precisionAtK()), decimal(metric.recallAtK()),
                        decimal(metric.hitRateAtK()), decimal(metric.catalogCoverage()),
                        decimal(metric.averageLatencyMs()), decimal(metric.p95LatencyMs()),
                        Integer.toString(metric.evaluatedUsers())));
                writer.newLine();
            }
        }
    }

    private void writeSamples(Path path, RecommendationAlg2Report report) throws IOException {
        try (BufferedWriter writer = writer(path)) {
            writer.write("sample_label,cohort,method,fallback,fallback_reason,rank,book_id,title,score");
            writer.newLine();
            for (RecommendationAlg2Report.SampleRecommendation sample : report.samples()) {
                for (RecommendationAlg2Report.Item item : sample.items()) {
                    writer.write(String.join(",",
                            csv(sample.sampleLabel()), csv(sample.cohort()), csv(sample.method()),
                            Boolean.toString(sample.fallback()), csv(sample.fallbackReason()),
                            Integer.toString(item.rank()), Long.toString(item.bookId()),
                            csv(item.title()), decimal(item.score())));
                    writer.newLine();
                }
            }
        }
    }

    private void writeReadme(Path path, RecommendationAlg2Report report) throws IOException {
        String content = """
                # ALG-2 - Hai mô hình đối chứng

                Bộ kết quả này so sánh **POPULARITY** và **CONTENT_TFIDF** trên cùng dữ liệu ALG-1.

                - Dữ liệu huấn luyện: chỉ phần `TRAIN`.
                - Dữ liệu đánh giá: chỉ phần `TEST`; `VALIDATION` không dùng để huấn luyện.
                - Ứng viên: %s
                - Top-K: %d
                - Số người dùng đánh giá: %d

                ## Tệp kết quả

                - `alg2_metrics.csv`: Precision@K, Recall@K, HitRate@K, Coverage và độ trễ.
                - `alg2_sample_top_k.csv`: danh sách gợi ý mẫu đã ẩn danh.
                - `alg2_report.json`: báo cáo đầy đủ có cảnh báo và chẩn đoán dữ liệu.
                - `alg2_config.json`: cấu hình bảo đảm hai mô hình dùng cùng quy tắc đánh giá.

                > %s

                Kết quả ALG-2 là baseline thực nghiệm; chưa bao gồm Hybrid/ALG-3 và không chứa họ tên,
                MSSV hay địa chỉ e-mail của sinh viên.
                """.formatted(
                report.candidateRule(), report.topK(), report.warning().evaluationUsers(),
                report.warning().message());
        Files.writeString(path, content, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private BufferedWriter writer(Path path) throws IOException {
        return Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.8f", value);
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return escaped.matches(".*[,\"\\r\\n].*") ? "\"" + escaped + "\"" : escaped;
    }

    private record Alg2Configuration(
            String datasetName,
            int topK,
            String candidateRule,
            String trainingData,
            String relevanceData,
            boolean anonymizedSamples) {
    }
}
