package com.huit.library.modules.policy.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class LibraryPolicyHistoryResponseDTO {
    private Long historyId;
    private Integer maximumOpenItems;
    private Integer homeLoanDays;
    private Integer renewalDays;
    private Integer maximumRenewalCount;
    private Integer waitlistHoldHours;
    private BigDecimal homeLateFinePerDay;
    private BigDecimal onsiteLateFinePerDay;
    private BigDecimal technicalProcessingFee;
    private Integer compensationSettlementDays;
    private UUID changedBy;
    private String changedByName;
    private String changedByEmail;
    private LocalDateTime changedAt;
}
