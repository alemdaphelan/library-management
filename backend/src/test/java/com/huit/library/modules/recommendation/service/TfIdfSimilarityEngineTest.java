package com.huit.library.modules.recommendation.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.entity.CategoryEntity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TfIdfSimilarityEngineTest {
    private final TfIdfSimilarityEngine engine = new TfIdfSimilarityEngine();

    @Test
    void ranksTechnicalBookAboveUnrelatedBook() {
        BookEntity source = book(1L, "Lập trình Java căn bản", "Nguyễn Văn A", "Công nghệ thông tin");
        BookEntity relevant = book(2L, "Java Spring Boot thực hành", "Nguyễn Văn B", "Công nghệ thông tin");
        BookEntity unrelated = book(3L, "Công nghệ chế biến thủy sản", "Trần Văn C", "Công nghệ thực phẩm");

        Map<Long, Double> scores = engine.score(
                source,
                List.of(relevant, unrelated),
                Map.of(
                        1L, List.of("SOFTWARE Lập trình và công nghệ phần mềm"),
                        2L, List.of("SOFTWARE Lập trình và công nghệ phần mềm"),
                        3L, List.of("FOOD Công nghệ thực phẩm")));

        assertThat(scores.get(2L)).isGreaterThan(scores.getOrDefault(3L, 0.0));
        assertThat(scores.get(2L)).isBetween(0.0, 1.0);
    }

    @Test
    void returnsNoScoresWhenSourceHasNoUsableMetadata() {
        BookEntity source = new BookEntity();
        source.setBookId(1L);
        BookEntity candidate = book(2L, "Cơ sở dữ liệu", null, null);

        assertThat(engine.score(source, List.of(candidate), Map.of())).isEmpty();
    }

    @Test
    void buildsWeightedProfileFromMultipleBorrowedBooks() {
        BookEntity javaHistory = book(1L, "Lập trình Java", null, "Công nghệ thông tin");
        BookEntity databaseHistory = book(2L, "Cơ sở dữ liệu", null, "Công nghệ thông tin");
        BookEntity relevant = book(3L, "Java và cơ sở dữ liệu", null, "Công nghệ thông tin");
        BookEntity unrelated = book(4L, "Văn học cổ điển", null, "Văn học");

        Map<Long, Double> scores = engine.scoreProfile(
                List.of(
                        new TfIdfSimilarityEngine.WeightedBook(javaHistory, 2.0),
                        new TfIdfSimilarityEngine.WeightedBook(databaseHistory, 1.0)),
                List.of(relevant, unrelated),
                Map.of());

        assertThat(scores.get(3L)).isGreaterThan(scores.getOrDefault(4L, 0.0));
    }

    private BookEntity book(Long id, String title, String author, String categoryName) {
        BookEntity book = new BookEntity();
        book.setBookId(id);
        book.setTitle(title);
        book.setAuthor(author);
        if (categoryName != null) {
            CategoryEntity category = new CategoryEntity();
            category.setCategoryId(id);
            category.setName(categoryName);
            book.setCategory(category);
        }
        return book;
    }
}
