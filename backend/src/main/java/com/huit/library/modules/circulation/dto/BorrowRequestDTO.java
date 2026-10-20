package com.huit.library.modules.circulation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;

@Data
public class BorrowRequestDTO {
    @NotNull(message = "Book ID must not be null")
    private Long bookId;
    
    @Pattern(regexp = "BORROW|ONSITE", message = "Ticket type must be BORROW or ONSITE")
    private String ticketType = "BORROW";
    
    // Ngày dự kiến đến nhận sách
    private LocalDate pickupDate;
    
    @Size(max = 500, message = "Note must not exceed 500 characters")
    private String note;
}
