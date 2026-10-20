package com.huit.library.modules.rag.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.rag.model.RagRetrievedContext;
import org.springframework.data.domain.PageRequest;
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
                    + "; năm xuất bản: " + (book.getPublishYear() == null ? "chưa rõ" : book.getPublishYear()) + ".";
            results.add(new RagRetrievedContext(
                    "catalog-book-" + book.getBookId(),
                    safe(book.getTitle()),
                    "/book/" + book.getBookId(),
                    "CATALOG",
                    content,
                    Math.max(0.55, 0.82 - rank * 0.04)));
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
                || normalized.contains("giao trinh");
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
        String searchable = title + " " + normalize(safe(book.getAuthor())) + " " + normalize(safe(book.getIsbn()));
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
