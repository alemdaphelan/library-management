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
public class LoanItemDTO {
    private Long ticketId;
    private Long detailId;
    private Long bookId;
    private String bookTitle;
    private String bookCover;
    private String author;
    private String barcode;
    private String ticketType;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime dueDate;
    private LocalDateTime returnDate;
    private Long daysRemaining;
    private Integer renewalCount;
}
