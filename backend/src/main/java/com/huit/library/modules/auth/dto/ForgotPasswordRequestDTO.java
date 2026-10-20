package com.huit.library.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ForgotPasswordRequestDTO {
    @NotBlank(message = "Email or student ID is required")
    @Size(max = 255, message = "Account identifier is too long")
    private String email;
}
