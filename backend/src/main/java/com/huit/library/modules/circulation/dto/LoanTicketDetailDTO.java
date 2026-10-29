package com.huit.library.modules.circulation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class LoanTicketDetailDTO {
    private Long ticketId;
    private UUID userId;
    private String mssv;
    private String readerName;
    private String ticketType;
    private String status;
    private String decisionNote;
    private LocalDateTime createdAt;
    private List<LoanItemDTO> items;
}
