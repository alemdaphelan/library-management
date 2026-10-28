package com.huit.library.modules.policy.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class LibraryPolicyResponseDTO {
    private Integer maximumOpenItems;
    private Integer homeLoanDays;
    private Integer renewalDays;
    private Integer maximumRenewalCount;
    private Integer waitlistHoldHours;
    private BigDecimal homeLateFinePerDay;
    private BigDecimal onsiteLateFinePerDay;
    private BigDecimal technicalProcessingFee;
    private Integer compensationSettlementDays;
    private UUID updatedBy;
    private LocalDateTime updatedAt;
}
