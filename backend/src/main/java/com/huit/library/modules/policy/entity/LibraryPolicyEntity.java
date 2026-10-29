package com.huit.library.modules.policy.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "library_policies")
public class LibraryPolicyEntity extends BaseEntity {
    @Id
    @Column(name = "policy_id")
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

    private UUID updatedBy;
}
