package com.huit.library.modules.finance.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "transaction_adjustments")
public class TransactionAdjustmentEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long adjustmentId;
    private Long transactionId;
    private BigDecimal requestedAmount;
    private String reason;
    private String status;
    private UUID requestedBy;
    private UUID reviewedBy;
    private String reviewNote;
    private Long reversalTransactionId;
    private LocalDateTime reviewedAt;
}
