package com.huit.library.modules.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

public record CatalogIdentificationDTO(Long bookId, String title, String isbn, String canonicalIsbn, String author,
        String publisher, Integer publishYear, BigDecimal defaultPrice, Long categoryId, String categoryName,
        String ddcCallNumber, List<Subject> subjects, String classificationStatus,
        String placementStatus, Long suggestedShelfId, String placementMessage) {
    public record Subject(String code, String name, BigDecimal confidence, String source, String evidence, boolean primary) {}
}
