package com.huit.library.modules.auth.dto;

import lombok.Data;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Data
public class UpdateProfileRequestDTO {
    @Size(max = 150)
    private String fullName;
    @Size(max = 24)
    @Pattern(regexp = "^$|^[0-9+().\\s-]{8,24}$", message = "Số điện thoại không đúng định dạng")
    private String phone;
    private String avatarUrl;
}
