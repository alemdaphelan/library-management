package com.huit.library.modules.circulation.dto;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
@Data public class ReturnRequestDTO { 
    @NotBlank private String barcode; 
    private String returnConditionNote; 
}