package com.huit.library.modules.rag.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.rag.model.RagRetrievedContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class RagCatalogRetriever {
    private static final Set<String> STOP_WORDS = Set.of(
            "sach", "tai", "lieu", "thu", "vien", "tim", "cho", "toi", "co", "khong", "nao", "ve", "cua",
            "tac", "gia");

    private final BookRepository bookRepository;

    public RagCatalogRetriever(BookRepository bookRepository) {
        this.bookRepository = bookRepository;
    }

    public List<RagRetrievedContext> retrieve(String query, int limit) {
        if (!looksLikeCatalogQuestion(query)) {
            return List.of();
        }
        List<String> queryTerms = meaningfulTokens(query);
        LinkedHashMap<Long, BookEntity> matches = new LinkedHashMap<>();
        if (looksLikeMoodRecommendation(query)) {
            for (String term : List.of("tâm lý", "kỹ năng sống", "truyền cảm hứng", "tình cảm", "văn học")) {
                bookRepository.searchCatalog(term, "", null, "", PageRequest.of(0, Math.max(10, limit * 4),
                                Sort.by(Sort.Direction.DESC, "view_count")))
                        .getContent()
                        .forEach(book -> matches.putIfAbsent(book.getBookId(), book));
            }
        }
        for (String term : searchTerms(query)) {
            bookRepository.findByTitleContainingIgnoreCaseOrIsbnContainingIgnoreCaseOrAuthorContainingIgnoreCase(
                            term, term, term, PageRequest.of(0, Math.max(20, limit * 10)))
                    .getContent()
                    .forEach(book -> matches.putIfAbsent(book.getBookId(), book));
        }
        List<RagRetrievedContext> results = new ArrayList<>();
        int rank = 0;
        List<BookEntity> rankedMatches = matches.values().stream()
                .sorted(Comparator.comparingDouble((BookEntity book) -> relevance(book, query, queryTerms)).reversed()
                        .thenComparing(BookEntity::getBookId))
                .toList();
        for (BookEntity book : rankedMatches) {
            if (results.size() >= limit) {
                break;
            }
            String category = book.getCategory() == null ? "Chưa phân loại" : book.getCategory().getName();
            String content = "Tên sách: " + safe(book.getTitle())
                    + "; tác giả: " + safe(book.getAuthor()).replace(".", "")
                    + "; ISBN: " + safe(book.getIsbn())
                    + "; thể loại: " + category
                    + "; nội dung: " + safe(book.getDescription())
                    + "; năm xuất bản: " + (book.getPublishYear() == null ? "chưa rõ" : book.getPublishYear()) + ".";
            results.add(new RagRetrievedContext(
                    "catalog-book-" + book.getBookId(),
                    safe(book.getTitle()),
                    "/book/" + book.getBookId(),
                    "CATALOG",
                    content,
                    Math.max(0.55, (looksLikeMoodRecommendation(query) ? 0.94 : 0.82) - rank * 0.04)));
            rank++;
        }
        return results;
    }

    private boolean looksLikeCatalogQuestion(String query) {
        String normalized = normalize(query);
        return normalized.contains("tim sach")
                || normalized.contains("co sach")
                || normalized.contains("sach nao")
                || normalized.contains("tac gia")
                || normalized.contains("isbn")
                || normalized.contains("giao trinh")
                || looksLikeMoodRecommendation(query);
    }

    boolean looksLikeMoodRecommendation(String query) {
        String normalized = normalize(query);
        return normalized.contains("buon")
                || normalized.contains("tam trang")
                || normalized.contains("cang thang")
                || normalized.contains("stress")
                || normalized.contains("lo au")
                || normalized.contains("truyen cam hung")
                || normalized.contains("dong luc")
                || normalized.contains("thu gian");
    }

    private List<String> searchTerms(String query) {
        List<String> tokens = meaningfulTokens(query);
        List<String> terms = new ArrayList<>();
        for (int index = 0; index < tokens.size() - 1; index++) {
            terms.add(tokens.get(index) + " " + tokens.get(index + 1));
        }
        terms.addAll(tokens);
        return terms.stream()
                .distinct()
                .limit(10)
                .toList();
    }

    private List<String> meaningfulTokens(String query) {
        return java.util.Arrays.stream(normalize(query).split("\\s+"))
                .filter(token -> token.length() >= 3 && !STOP_WORDS.contains(token))
                .distinct()
                .limit(8)
                .toList();
    }

    private double relevance(BookEntity book, String query, List<String> queryTerms) {
        String normalizedQuery = normalize(query);
        String title = normalize(safe(book.getTitle()));
        String searchable = title + " " + normalize(safe(book.getAuthor())) + " " + normalize(safe(book.getIsbn()))
                + " " + normalize(safe(book.getDescription()))
                + " " + normalize(book.getCategory() == null ? "" : book.getCategory().getName());
        long matchingTerms = queryTerms.stream().filter(searchable::contains).count();
        double exactTitleBonus = title.length() >= 5 && normalizedQuery.contains(title) ? 50.0 : 0.0;
        return exactTitleBonus + matchingTerms * 10.0 - title.length() * 0.001;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private String safe(String value) {
        return value == null || value.isBlank() ? "chưa rõ" : value.trim();
    }
}
