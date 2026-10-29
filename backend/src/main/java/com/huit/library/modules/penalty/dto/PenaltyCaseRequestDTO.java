package com.huit.library.modules.penalty.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PenaltyCaseRequestDTO {
    @NotNull
    private Long loanDetailId;
    @NotBlank
    private String incidentType;
    private String damageSeverity;
    private Integer pagesAffected;
    @NotBlank
    @Size(max = 1000)
    private String description;
    @Size(max = 500)
    private String evidenceUrl;
    @NotBlank
    private String materialType;
    @NotBlank
    private String languageCategory;
    private Boolean stillInMarket;
    @NotBlank
    private String settlementMethod;
    @DecimalMin("0")
    private BigDecimal coverPrice;
    @Size(max = 3)
    private String originalCurrency;
    @DecimalMin("0.000001")
    private BigDecimal fxRate;
    @Size(max = 255)
    private String fxSource;
    @DecimalMin("0")
    private BigDecimal repairFee;
}
