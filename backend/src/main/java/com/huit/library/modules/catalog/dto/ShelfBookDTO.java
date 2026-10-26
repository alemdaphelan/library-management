package com.huit.library.modules.catalog.dto;

public record ShelfBookDTO(
        String barcode,
        Long bookId,
        String title,
        String isbn,
        String status) {
}
