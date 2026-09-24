package com.huit.library.modules.finance.dto;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
@Data public class PaymentRequestDTO { 
    @NotNull private Long transactionId; 
    @NotNull private String method; 
}