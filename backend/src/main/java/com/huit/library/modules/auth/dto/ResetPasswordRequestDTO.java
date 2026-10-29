package com.huit.library.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequestDTO {
    @NotBlank(message = "Email or student ID is required")
    @Size(max = 255, message = "Account identifier is too long")
    private String email;

    @NotBlank(message = "OTP is required")
    @Pattern(regexp = "\\d{6}", message = "OTP must contain exactly 6 digits")
    private String otp;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 128, message = "New password must contain between 8 and 128 characters")
    private String newPassword;
}
