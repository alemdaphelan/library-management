package com.huit.library.modules.circulation.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "loan_details")
public class LoanDetailEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long detailId;

    private Long ticketId;
    private String barcode;
    private String checkoutConditionNote;
    private String returnConditionNote;
    private String returnStatus;
    
    private LocalDateTime dueDate;
    private LocalDateTime returnDate;
    private LocalDateTime checkoutAt;
    private String checkoutTimeSource;
    private LocalDateTime physicalReturnAt;
    private String physicalReturnTimeSource;
    private Long circulationBookId;
    private Integer renewalCount = 0;
    private Boolean isRecalled = false;
    private LocalDateTime recalledAt;
    private LocalDateTime recallDueAt;
    private LocalDateTime originalDueDateBeforeRecall;
    private LocalDateTime recallRequestedAt;
    private java.util.UUID recallRequestedBy;
    @Column(length = 1000)
    private String recallReason;
    private LocalDateTime recallPreviousDueDate;
    private LocalDateTime recallDueDate;
    private BigDecimal lateFineAccrued = BigDecimal.ZERO;
    private LocalDateTime fineStoppedAt;
    private LocalDateTime lossReportedAt;
    @Column(length = 1000)
    private String lossReportDescription;

    public void recordRecall(LocalDateTime at, java.util.UUID actor, String reason,
            LocalDateTime previousDueDate, LocalDateTime effectiveDueDate) {
        isRecalled = true;
        recallRequestedAt = recalledAt = at;
        recallRequestedBy = actor;
        recallReason = reason;
        recallPreviousDueDate = originalDueDateBeforeRecall = previousDueDate;
        recallDueDate = recallDueAt = effectiveDueDate;
        dueDate = effectiveDueDate;
    }
}
