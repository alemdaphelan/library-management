package com.huit.library.modules.circulation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QrResolveRequestDTO {
    @NotBlank(message = "QR payload must not be blank")
    @Size(max = 2048, message = "QR payload is too long")
    private String payload;
}
