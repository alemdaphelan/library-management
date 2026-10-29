package com.huit.library.modules.finance.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "transactions")
public class TransactionEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long transactionId;

    private Long parentTransactionId;
    private String adminRef;
    private String proofDocumentUrl;
    private BigDecimal amount;
    private String type; // e.g., FINE, DEPOSIT, REFUND
    private UUID userId;
    private Long ticketId;
    private Long loanDetailId;
    private Long penaltyCaseId;
    private String reason;
    private String status;
    private String paymentMethod;
    private String receiptNumber;
    private UUID confirmedBy;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private LocalDateTime paidAt;
    // Captured once when the charge consumes the deposit; later payments must not overwrite it.
    private BigDecimal depositAppliedAmount;
    private LocalDateTime depositAppliedAt;
    @Column(unique = true, length = 160)
    private String dedupKey;
}
