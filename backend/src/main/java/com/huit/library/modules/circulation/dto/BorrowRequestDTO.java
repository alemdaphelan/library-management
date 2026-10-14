package com.huit.library.modules.circulation.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class BorrowRequestDTO {
    @NotNull(message = "Book ID must not be null")
    private Long bookId;
    
    // "BORROW" (mượn mang về) hoặc "ONSITE" (đọc tại chỗ)
    private String ticketType = "BORROW";
    
    // Ngày dự kiến đến nhận sách
    private LocalDate pickupDate;
    
    // Ghi chú nếu có
    private String note;
}
