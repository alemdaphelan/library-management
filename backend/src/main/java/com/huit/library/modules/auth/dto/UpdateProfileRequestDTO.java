package com.huit.library.modules.auth.dto;

import lombok.Data;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
public class UpdateProfileRequestDTO {
    @Size(max = 150)
    private String fullName;
    @Size(max = 20)
    @Pattern(regexp = "^$|^[0-9+() -]{8,20}$", message = "Số điện thoại phải có từ 8 đến 20 ký tự hợp lệ")
    private String phone;
    private String avatarUrl;
}
