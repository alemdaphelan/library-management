package com.huit.library.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InventoryScanRequestDTO {
    @NotBlank(message = "Barcode không được để trống")
    private String barcode;

    @jakarta.validation.constraints.Size(max = 1000)
    private String scannedLocation;
    @jakarta.validation.constraints.Positive
    private Long scannedShelfId;
}
