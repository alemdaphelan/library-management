package com.huit.library.modules.circulation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CheckoutRequestDTO {
    @NotBlank(message = "Idempotency key must not be blank")
    @Size(max = 100, message = "Idempotency key must not exceed 100 characters")
    private String idempotencyKey;

    @NotNull(message = "Ticket ID must be specified")
    private Long ticketId;

    @NotEmpty(message = "Barcodes list cannot be empty")
    private List<@Valid @NotBlank(message = "Barcode must not be blank") String> barcodes;
}
