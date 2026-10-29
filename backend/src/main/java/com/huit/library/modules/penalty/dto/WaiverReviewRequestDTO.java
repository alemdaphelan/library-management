package com.huit.library.modules.penalty.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class WaiverReviewRequestDTO {
    @NotBlank
    private String action;
    @NotBlank
    @Size(max = 255)
    private String externalDecisionMaker;
    @NotBlank
    @Size(max = 100)
    private String decisionDocumentNo;
    @NotNull
    private LocalDate decisionDate;
    @DecimalMin("0")
    private BigDecimal waiverAmount;
    @NotBlank
    @Size(max = 1000)
    private String reason;
}
