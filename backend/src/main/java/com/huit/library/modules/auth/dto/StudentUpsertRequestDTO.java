package com.huit.library.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record StudentUpsertRequestDTO(
        @NotBlank @Size(max = 30) String mssv,
        @NotBlank @Size(max = 255) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 255) String department,
        @Pattern(regexp = "^$|^[0-9+() .-]{8,20}$", message = "Số điện thoại không hợp lệ") String phone,
        @Pattern(regexp = "ACTIVE|LOCKED|EXPIRED", message = "Trạng thái thẻ không hợp lệ") String cardStatus,
        LocalDateTime cardExpiryDate
) {
}
