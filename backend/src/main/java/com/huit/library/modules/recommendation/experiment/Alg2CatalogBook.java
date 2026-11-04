package com.huit.library.modules.recommendation.experiment;

import java.util.List;

public record Alg2CatalogBook(
        Long bookId,
        String title,
        String author,
        String publisher,
        String category,
        String description,
        List<String> subjects) {

    public Alg2CatalogBook {
        subjects = subjects == null ? List.of() : List.copyOf(subjects);
    }
}
