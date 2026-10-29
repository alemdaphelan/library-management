package com.huit.library.modules.circulation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class CirculationOperationResponseDTO {
    private UUID operationId;
    private String operationType;
    private List<Long> ticketIds;
    private List<String> barcodes;
    private String status;
    private String message;
    private LocalDateTime processedAt;
    private boolean replayed;
}
