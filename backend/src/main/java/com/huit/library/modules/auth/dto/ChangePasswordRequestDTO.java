package com.huit.library.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordRequestDTO {
    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 128, message = "New password must contain between 8 and 128 characters")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[^A-Za-z0-9\\s]).{8,128}$",
            message = "Mật khẩu mới phải có chữ hoa, chữ thường và ký tự đặc biệt")
    private String newPassword;
}
