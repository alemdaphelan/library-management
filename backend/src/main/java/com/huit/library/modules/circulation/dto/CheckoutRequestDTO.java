package com.huit.library.modules.circulation.dto;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.List;
@Data public class CheckoutRequestDTO { 
    @NotEmpty(message = "Barcodes list cannot be empty") private List<String> barcodes; 
    @NotNull(message = "Ticket type must be specified") private String ticketType; 
}