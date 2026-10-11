package com.huit.library.modules.circulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BorrowResponseDTO {
    private boolean success;
    private String message;
    private Long ticketId;
    private String ticketType;
    private String status; // "PENDING", "RESERVED", "WAITLIST"
    private String barcode;
    private LocalDateTime pickupDate;
    private LocalDateTime dueDate;
    private String bookTitle;
}
