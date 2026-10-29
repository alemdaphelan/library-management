package com.huit.library.modules.circulation.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class BatchBorrowResponseDTO {
    private boolean success;
    private String message;
    private Long ticketId;
    private String ticketType;
    private String status;
    private LocalDateTime pickupDeadline;
    private LocalDateTime dueDate;
    private List<Long> bookIds;
    private List<String> bookTitles;
    private List<String> barcodes;
}
