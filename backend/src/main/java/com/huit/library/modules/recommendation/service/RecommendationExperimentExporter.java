package com.huit.library.modules.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Interaction;
import com.huit.library.modules.recommendation.dto.RecommendationExperimentDataset.Split;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Locale;

/**
 * Writes the ALG-1 dataset without student names, e-mail addresses or other PII.
 */
@Service
@RequiredArgsConstructor
public class RecommendationExperimentExporter {

    private static final String INTERACTION_HEADER =
            String.join(
                    ",",
                    "user_id",
                    "book_id",
                    "first_interaction_at",
                    "last_interaction_at",
                    "occurrence_count",
                    "renewal_count",
                    "preference_weight",
                    "split",
                    "user_cohort");

    private final ObjectMapper objectMapper;

    public Path export(RecommendationExperimentDataset dataset, Path outputDirectory)
            throws IOException {
        Path output = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(output);

        writeJson(output.resolve("summary.json"), dataset.summary());
        writeJson(
                output.resolve("config.json"),
                new ExportConfiguration(
                        dataset.datasetName(),
                        dataset.generatedAt(),
                        dataset.splitStrategy(),
                        "Positive interactions are checked-out/borrowing/overdue/returned/lost/damaged/settled loan details; "
                                + "cancelled and rejected records are excluded.",
                        "Repeated user-book events are collapsed into one pair before chronological splitting.",
                        "1 + 0.15 * min(total renewals, 2) + 0.10 * min(repeated borrows, 2)"));

        writeInteractions(output.resolve("interactions_clean.csv"), dataset.interactions());
        writeInteractions(
                output.resolve("train.csv"), filterBySplit(dataset.interactions(), Split.TRAIN));
        writeInteractions(
                output.resolve("validation.csv"),
                filterBySplit(dataset.interactions(), Split.VALIDATION));
        writeInteractions(
                output.resolve("test.csv"), filterBySplit(dataset.interactions(), Split.TEST));
        writeIdentifiers(output.resolve("new_users.csv"), "user_id", dataset.newUserIds());
        writeIdentifiers(output.resolve("new_books.csv"), "book_id", dataset.newBookIds());
        writeText(output.resolve("data_dictionary.md"), dataDictionary());
        writeText(output.resolve("README.md"), readme(dataset));
        return output;
    }

    private List<Interaction> filterBySplit(List<Interaction> interactions, Split split) {
        return interactions.stream().filter(interaction -> interaction.split() == split).toList();
    }

    private void writeJson(Path path, Object value) throws IOException {
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
    }

    private void writeInteractions(Path path, List<Interaction> interactions) throws IOException {
        try (BufferedWriter writer = newWriter(path)) {
            writer.write(INTERACTION_HEADER);
            writer.newLine();
            for (Interaction interaction : interactions) {
                writer.write(
                        String.join(
                                ",",
                                csv(interaction.userId()),
                                csv(interaction.bookId()),
                                csv(interaction.firstInteractionAt()),
                                csv(interaction.lastInteractionAt()),
                                csv(interaction.occurrenceCount()),
                                csv(interaction.renewalCount()),
                                String.format(Locale.ROOT, "%.6f", interaction.preferenceWeight()),
                                csv(interaction.split()),
                                csv(interaction.cohort())));
                writer.newLine();
            }
        }
    }

    private void writeIdentifiers(Path path, String header, List<?> values) throws IOException {
        try (BufferedWriter writer = newWriter(path)) {
            writer.write(header);
            writer.newLine();
            for (Object value : values) {
                writer.write(csv(value));
                writer.newLine();
            }
        }
    }

    private BufferedWriter newWriter(Path path) throws IOException {
        return Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private void writeText(Path path, String content) throws IOException {
        Files.writeString(
                path,
                content,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

    private String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",")
                || text.contains("\"")
                || text.contains("\n")
                || text.contains("\r")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    private String readme(RecommendationExperimentDataset dataset) {
        return """
                # ALG-1 - Bộ dữ liệu thực nghiệm gợi ý sách

                Bộ dữ liệu này được tạo tự động từ dữ liệu mượn thật của hệ thống. Các tệp chỉ chứa mã định danh,
                không chứa họ tên, email, số điện thoại hoặc thông tin cá nhân khác.

                ## Chiến lược chia tập

                %s

                - Việc chia tập được thực hiện theo thời gian trong từng sinh viên, không xáo trộn ngẫu nhiên.
                - Một cặp sinh viên-sách chỉ xuất hiện trong đúng một tập.
                - Tập kiểm tra không được dùng để chọn trọng số ở các gói sau.

                ## Các tệp

                - `summary.json`: thống kê quy mô, thiếu dữ liệu, độ thưa và nhóm cold-start.
                - `interactions_clean.csv`: toàn bộ cặp tương tác hợp lệ sau làm sạch.
                - `train.csv`, `validation.csv`, `test.csv`: ba tập dùng chung khi so sánh thuật toán.
                - `new_users.csv`: sinh viên không có lịch sử trong tập huấn luyện.
                - `new_books.csv`: sách chưa xuất hiện trong tập huấn luyện.
                - `config.json`: quy tắc tạo dữ liệu và trọng số phản hồi ngầm định.
                - `data_dictionary.md`: giải thích từng cột.

                ## Quy mô tại thời điểm xuất

                - Sinh viên: %d
                - Sách đủ điều kiện: %d
                - Tương tác hợp lệ ban đầu: %d
                - Cặp sinh viên-sách duy nhất: %d
                - Độ thưa: %.6f

                Kết quả phụ thuộc vào trạng thái CSDL tại thời điểm xuất `%s`. Chạy lại cùng mã nguồn và cùng ảnh chụp
                CSDL sẽ cho cùng phép làm sạch và chia tập; thời điểm tạo tệp có thể khác.
                """
                .formatted(
                        dataset.splitStrategy(),
                        dataset.summary().totalStudents(),
                        dataset.summary().totalEligibleBooks(),
                        dataset.summary().validRawInteractions(),
                        dataset.summary().uniqueUserBookInteractions(),
                        dataset.summary().sparsity(),
                        dataset.generatedAt());
    }

    private String dataDictionary() {
        return """
                # Từ điển dữ liệu ALG-1

                | Cột | Ý nghĩa |
                | --- | --- |
                | `user_id` | Mã UUID nội bộ của sinh viên; không phải MSSV và không chứa PII. |
                | `book_id` | Mã đầu sách trong danh mục. |
                | `first_interaction_at` | Thời điểm tương tác hợp lệ đầu tiên của cặp sinh viên-sách. |
                | `last_interaction_at` | Thời điểm tương tác hợp lệ gần nhất của cùng cặp. |
                | `occurrence_count` | Số lượt mượn hợp lệ đã được gộp vào cặp. |
                | `renewal_count` | Tổng số lần gia hạn của các lượt đã gộp. |
                | `preference_weight` | Trọng số phản hồi ngầm định, giới hạn ảnh hưởng của mượn lặp và gia hạn. |
                | `split` | `TRAIN`, `VALIDATION` hoặc `TEST`. |
                | `user_cohort` | `NEW`, `SPARSE` hoặc `WARM`, xác định chỉ từ lịch sử trong tập huấn luyện. |

                `summary.json` dùng tỷ lệ phần trăm trên thang 0-100. `sparsity` dùng thang 0-1 và được tính bằng
                `1 - số cặp sinh viên-sách duy nhất / (tổng sinh viên * tổng sách đủ điều kiện)`.
                """;
    }

    private record ExportConfiguration(
            String datasetName,
            Object generatedAt,
            String splitStrategy,
            String positiveInteractionRule,
            String repeatedInteractionRule,
            String preferenceWeightFormula) {}
}
