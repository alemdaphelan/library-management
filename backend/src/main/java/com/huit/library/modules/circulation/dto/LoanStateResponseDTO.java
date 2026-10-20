package com.huit.library.modules.circulation.dto;

import com.huit.library.modules.circulation.model.LoanTicketStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class LoanStateResponseDTO {
    private Long ticketId;
    private LoanTicketStatus previousStatus;
    private LoanTicketStatus status;
    private String message;
    private LocalDateTime changedAt;
}
