package com.huit.library.modules.inventory.dto;

import java.math.BigDecimal;
import java.util.List;

public record ReceiptLineResponseDTO(Long detailId, Long bookId, String title, String isbn,
        int quantity, BigDecimal unitPrice, Long shelfId, String locationPath, List<String> barcodes) {}
