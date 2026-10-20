package com.huit.library.modules.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InventoryScanRequestDTO {
    @NotBlank(message = "Barcode không được để trống")
    private String barcode;

    private String scannedLocation;
}
