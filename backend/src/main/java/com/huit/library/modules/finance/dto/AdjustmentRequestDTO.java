package com.huit.library.modules.finance.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class AdjustmentRequestDTO {
    @NotNull
    private Long transactionId;
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;
    @NotBlank
    @Size(max = 1000)
    private String reason;
}
