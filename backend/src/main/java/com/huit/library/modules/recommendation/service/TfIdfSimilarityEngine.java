package com.huit.library.modules.recommendation.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Component
public class TfIdfSimilarityEngine {
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "the", "of", "for", "to", "in", "on", "with",
            "va", "voi", "cua", "cho", "trong", "mot", "nhung", "cac", "ve"
    );

    public Map<Long, Double> score(
            BookEntity source,
            List<BookEntity> candidates,
            Map<Long, List<String>> subjectTermsByBookId) {
        return scoreProfile(
                List.of(new WeightedBook(source, 1.0)),
                candidates,
                subjectTermsByBookId);
    }

    public Map<Long, Double> scoreProfile(
            List<WeightedBook> profileBooks,
            List<BookEntity> candidates,
            Map<Long, List<String>> subjectTermsByBookId) {
        LinkedHashMap<Long, BookEntity> uniqueDocuments = new LinkedHashMap<>();
        profileBooks.stream()
                .map(WeightedBook::book)
                .forEach(book -> uniqueDocuments.putIfAbsent(book.getBookId(), book));
        candidates.forEach(book -> uniqueDocuments.putIfAbsent(book.getBookId(), book));
        List<BookEntity> documents = new ArrayList<>(uniqueDocuments.values());

        Map<Long, Map<String, Double>> termFrequencies = new HashMap<>();
        Map<String, Integer> documentFrequencies = new HashMap<>();
        for (BookEntity document : documents) {
            Map<String, Double> terms = weightedTerms(
                    document,
                    subjectTermsByBookId.getOrDefault(document.getBookId(), List.of()));
            termFrequencies.put(document.getBookId(), terms);
            for (String term : terms.keySet()) {
                documentFrequencies.merge(term, 1, Integer::sum);
            }
        }

        Map<String, Double> profileVector = new HashMap<>();
        double totalProfileWeight = profileBooks.stream()
                .mapToDouble(profile -> Math.max(profile.weight(), 0.0))
                .sum();
        if (totalProfileWeight == 0) {
            return Map.of();
        }
        for (WeightedBook profileBook : profileBooks) {
            double weight = Math.max(profileBook.weight(), 0.0) / totalProfileWeight;
            Map<String, Double> bookVector = tfIdf(
                    termFrequencies.getOrDefault(profileBook.book().getBookId(), Map.of()),
                    documentFrequencies,
                    documents.size());
            bookVector.forEach((term, value) -> profileVector.merge(term, value * weight, Double::sum));
        }

        double profileNorm = norm(profileVector.values());
        Map<Long, Double> scores = new HashMap<>();
        if (profileNorm == 0) {
            return scores;
        }

        for (BookEntity candidate : candidates) {
            Map<String, Double> candidateVector = tfIdf(
                    termFrequencies.getOrDefault(candidate.getBookId(), Map.of()),
                    documentFrequencies,
                    documents.size());
            double denominator = profileNorm * norm(candidateVector.values());
            if (denominator == 0) {
                continue;
            }
            double dotProduct = profileVector.entrySet().stream()
                    .mapToDouble(entry -> entry.getValue() * candidateVector.getOrDefault(entry.getKey(), 0.0))
                    .sum();
            double similarity = dotProduct / denominator;
            if (similarity > 0) {
                scores.put(candidate.getBookId(), similarity);
            }
        }
        return scores;
    }

    public record WeightedBook(BookEntity book, double weight) {
    }

    private Map<String, Double> weightedTerms(BookEntity book, List<String> subjects) {
        Map<String, Double> terms = new HashMap<>();
        addField(terms, book.getTitle(), 3.0);
        addField(terms, book.getAuthor(), 1.0);
        addField(terms, book.getPublisher(), 1.0);
        if (book.getCategory() != null) {
            addField(terms, book.getCategory().getName(), 2.0);
        }
        subjects.forEach(subject -> addField(terms, subject, 4.0));
        return terms;
    }

    private void addField(Map<String, Double> terms, String value, double weight) {
        List<String> tokens = tokenize(value);
        for (String token : tokens) {
            terms.merge(token, weight, Double::sum);
        }
        for (int index = 0; index + 1 < tokens.size(); index++) {
            terms.merge(tokens.get(index) + "_" + tokens.get(index + 1), weight, Double::sum);
        }
    }

    private List<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String normalized = Normalizer.normalize(value.toLowerCase(Locale.ROOT).replace("c++", "cpp"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('đ', 'd')
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String token : normalized.split("\\s+")) {
            if (token.length() >= 2 && !STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private Map<String, Double> tfIdf(
            Map<String, Double> termFrequency,
            Map<String, Integer> documentFrequency,
            int documentCount) {
        Map<String, Double> vector = new HashMap<>();
        termFrequency.forEach((term, frequency) -> {
            double tf = 1.0 + Math.log(frequency);
            double idf = Math.log((documentCount + 1.0) / (documentFrequency.getOrDefault(term, 0) + 1.0)) + 1.0;
            vector.put(term, tf * idf);
        });
        return vector;
    }

    private double norm(Collection<Double> values) {
        return Math.sqrt(values.stream().mapToDouble(value -> value * value).sum());
    }
}
