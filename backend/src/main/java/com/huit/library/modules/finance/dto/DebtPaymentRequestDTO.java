package com.huit.library.modules.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class DebtPaymentRequestDTO {
    @NotNull
    private UUID userId;
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;
    @NotBlank
    private String method;
    @NotBlank
    @Size(max = 100)
    private String idempotencyKey;
    @Size(max = 500)
    private String note;
}
