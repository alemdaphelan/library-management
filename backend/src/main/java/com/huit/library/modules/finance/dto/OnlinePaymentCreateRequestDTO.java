package com.huit.library.modules.finance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class OnlinePaymentCreateRequestDTO {
    @NotNull
    @DecimalMin("1000")
    @DecimalMax("100000000")
    private BigDecimal amount;

    @NotBlank
    private String purpose;
}
