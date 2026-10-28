package com.huit.library.modules.policy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "library_policy_history")
public class LibraryPolicyHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    @Column(nullable = false)
    private Long policyId;

    @Column(nullable = false)
    private Integer maximumOpenItems;

    @Column(nullable = false)
    private Integer homeLoanDays;

    @Column(nullable = false)
    private Integer renewalDays;

    @Column(nullable = false)
    private Integer maximumRenewalCount;

    @Column(nullable = false)
    private Integer waitlistHoldHours;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal homeLateFinePerDay;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal onsiteLateFinePerDay;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal technicalProcessingFee;

    @Column(nullable = false)
    private Integer compensationSettlementDays;

    private UUID changedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime changedAt;
}
