package com.huit.library.modules.circulation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ReturnRequestDTO {
    @NotBlank(message = "Idempotency key must not be blank")
    @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
    private String idempotencyKey;

    @NotEmpty(message = "Barcodes list cannot be empty")
    private List<@Valid @NotBlank(message = "Barcode must not be blank") String> barcodes;

    @Size(max = 500, message = "Return condition note must not exceed 500 characters")
    private String returnConditionNote;
}
