package com.huit.library.modules.inventory.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class GoodsReceiptResponseDTO {
    private Long receiptId;
    private String receiptCode;
    private LocalDateTime createdAt;
    private Long supplierId;
    private String supplierName;
    private int quantity;
    private BigDecimal totalAmount;
    private String status;
}
