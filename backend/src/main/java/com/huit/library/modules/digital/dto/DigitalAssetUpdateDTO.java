package com.huit.library.modules.digital.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DigitalAssetUpdateDTO(
        Long bookId,
        @NotBlank @Size(max = 255) String displayName,
        @Pattern(regexp = "AUTHENTICATED|STAFF_ONLY", message = "Mức truy cập không hợp lệ") String accessLevel
) {
}
