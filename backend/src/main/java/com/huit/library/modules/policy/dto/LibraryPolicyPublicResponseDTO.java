package com.huit.library.modules.policy.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class LibraryPolicyPublicResponseDTO {
    private Integer maximumOpenItems;
    private Integer homeLoanDays;
    private Integer renewalDays;
    private Integer maximumRenewalCount;
    private Integer waitlistHoldHours;
    private BigDecimal homeLateFinePerDay;
    private BigDecimal onsiteLateFinePerDay;
    private BigDecimal technicalProcessingFee;
    private Integer compensationSettlementDays;
}
