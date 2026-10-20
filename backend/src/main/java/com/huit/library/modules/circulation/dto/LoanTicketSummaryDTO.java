package com.huit.library.modules.circulation.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class LoanTicketSummaryDTO {
    private Long ticketId;
    private UUID userId;
    private String mssv;
    private String readerName;
    private String department;
    private String cardStatus;
    private String ticketType;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime nearestDueDate;
    private Integer itemCount;
    private Integer overdueItemCount;
    private BigDecimal totalLateFine;
}
