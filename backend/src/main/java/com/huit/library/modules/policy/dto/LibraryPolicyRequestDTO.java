package com.huit.library.modules.policy.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LibraryPolicyRequestDTO {
    @NotNull @Min(1) @Max(20)
    private Integer maximumOpenItems;

    @NotNull @Min(1) @Max(90)
    private Integer homeLoanDays;

    @NotNull @Min(1) @Max(90)
    private Integer renewalDays;

    @NotNull @Min(0) @Max(20)
    private Integer maximumRenewalCount;

    @NotNull @Min(1) @Max(336)
    private Integer waitlistHoldHours;

    @NotNull @DecimalMin("0")
    private BigDecimal homeLateFinePerDay;

    @NotNull @DecimalMin("0")
    private BigDecimal onsiteLateFinePerDay;

    @NotNull @DecimalMin("0")
    private BigDecimal technicalProcessingFee;

    @NotNull @Min(1) @Max(365)
    private Integer compensationSettlementDays;
}
