package com.huit.library.modules.circulation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class BatchBorrowRequestDTO {
    @NotEmpty(message = "Danh sách đầu sách không được để trống")
    private List<@Valid @NotNull(message = "Mã đầu sách không được để trống") Long> bookIds;

    @Pattern(regexp = "BORROW|ONSITE", message = "Ticket type must be BORROW or ONSITE")
    private String ticketType = "BORROW";

    private LocalDate pickupDate;

    @Size(max = 500, message = "Note must not exceed 500 characters")
    private String note;
}
