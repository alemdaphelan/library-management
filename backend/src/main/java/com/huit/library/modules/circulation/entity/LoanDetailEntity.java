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
    private Integer renewalCount = 0;
    private Boolean isRecalled = false;
    private BigDecimal lateFineAccrued = BigDecimal.ZERO;
}
