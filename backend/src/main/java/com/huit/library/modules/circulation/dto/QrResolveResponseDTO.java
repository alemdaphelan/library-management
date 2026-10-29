package com.huit.library.modules.circulation.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class QrResolveResponseDTO {
    private String type;
    private String subjectId;
    private UUID userId;
    private String readerName;
    private String mssv;
    private String cardStatus;
    private Long ticketId;
    private String ticketStatus;
    private List<String> allowedActions;
    private List<LoanItemDTO> items;
    private Integer activeLoanCount;
    private BigDecimal totalDebt;
    private Instant expiresAt;
}
