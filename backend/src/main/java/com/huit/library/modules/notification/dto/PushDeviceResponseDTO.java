package com.huit.library.modules.notification.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PushDeviceResponseDTO(
        UUID registrationId,
        String platform,
        String deviceId,
        boolean active,
        LocalDateTime lastSeenAt) {
}
