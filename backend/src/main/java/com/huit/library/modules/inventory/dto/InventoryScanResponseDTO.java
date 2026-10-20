package com.huit.library.modules.inventory.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class InventoryScanResponseDTO {
    private String detailId;
    private Long auditId;
    private String barcode;
    private String scanStatus;
    private String expectedLocation;
    private String scannedLocation;
    private Long bookId;
    private String bookTitle;
    private String copyStatus;
    private LocalDateTime scannedAt;
}
