package com.huit.library.modules.penalty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReplacementAcceptanceRequestDTO {
    @NotBlank
    @Size(max = 255)
    private String replacementBarcode;

    @NotBlank
    @Size(max = 1000)
    private String verificationNote;
}
