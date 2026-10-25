package com.huit.library.modules.circulation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ManualCheckoutRequestDTO {
    @NotBlank(message = "Idempotency key must not be blank")
    @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
    private String idempotencyKey;

    @NotBlank(message = "MSSV must not be blank")
    @Size(max = 30, message = "MSSV must not exceed 30 characters")
    private String mssv;

    @Pattern(regexp = "(?i)BORROW|ONSITE", message = "Ticket type must be BORROW or ONSITE")
    private String ticketType = "BORROW";

    @NotEmpty(message = "Barcodes list cannot be empty")
    @Size(max = 3, message = "A manual checkout cannot contain more than 3 books")
    private List<@Valid @NotBlank(message = "Barcode must not be blank") String> barcodes;

    @Size(max = 500, message = "Note must not exceed 500 characters")
    private String note;
}
