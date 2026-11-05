package com.huit.library.modules.circulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoanItemDTO {
    private Long ticketId;
    private Long detailId;
    private Long bookId;
    private String bookTitle;
    private String bookCover;
    private String author;
    private BigDecimal bookValue;
    private String bookValueSource;
    private String bookValueCurrency;
    private Boolean priceKnown;
    private Boolean returnable;
    private String barcode;
    private String ticketType;
    private String status;
    private String itemStatus;
    private String decisionNote;
    private LocalDateTime createdAt;
    private LocalDateTime borrowedAt;
    private LocalDateTime pickupDeadline;
    private LocalDateTime dueDate;
    private LocalDateTime returnDate;
    private LocalDateTime physicalReturnAt;
    private LocalDateTime lossReportedAt;
    private String lossReportDescription;
    private Boolean isRecalled;
    private LocalDateTime recallRequestedAt;
    private java.util.UUID recallRequestedBy;
    private String recallReason;
    private LocalDateTime recallPreviousDueDate;
    private LocalDateTime recallDueDate;
    private Long daysRemaining;
    private Integer renewalCount;
    private Boolean recalled;
    private LocalDateTime recalledAt;
    private LocalDateTime recallDueAt;
    private LocalDateTime originalDueDateBeforeRecall;
    private BigDecimal lateFineAccrued;
}
