package com.huit.library.modules.penalty.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class CompensationRateRequestDTO {
    @NotBlank
    private String materialType;
    @NotBlank
    private String languageCategory;
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;
    @NotNull
    private LocalDate validFrom;
    private LocalDate validUntil;
    @Size(max = 500)
    private String note;
}
