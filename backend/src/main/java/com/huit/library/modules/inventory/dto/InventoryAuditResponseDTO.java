package com.huit.library.modules.inventory.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class InventoryAuditResponseDTO {
    private Long auditId;
    private UUID conductedBy;
    private Integer totalSystemBooks;
    private Integer totalActualBooks;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<InventoryScanResponseDTO> details;
}
