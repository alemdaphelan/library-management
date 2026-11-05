package com.huit.library.modules.catalog.dto;

public record ShelfBookDTO(
        String barcode,
        Long bookId,
        String title,
        String isbn,
        String status,
        boolean catalogMatched) {
    public ShelfBookDTO(String barcode, Long bookId, String title, String isbn, String status) {
        this(barcode, bookId, title, isbn, status, true);
    }
}
