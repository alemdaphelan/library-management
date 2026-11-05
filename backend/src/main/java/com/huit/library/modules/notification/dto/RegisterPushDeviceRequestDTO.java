package com.huit.library.modules.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterPushDeviceRequestDTO(
        @NotBlank @Size(max = 512) String token,
        @NotBlank @Pattern(regexp = "ANDROID|IOS|WEB") String platform,
        @Size(max = 128) String deviceId,
        @Pattern(regexp = "TOKEN|FID") String identifierType) {
    public RegisterPushDeviceRequestDTO(String token, String platform, String deviceId) {
        this(token, platform, deviceId, null);
    }
}
