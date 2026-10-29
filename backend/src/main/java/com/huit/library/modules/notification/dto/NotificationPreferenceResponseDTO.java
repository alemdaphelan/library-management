package com.huit.library.modules.notification.dto;

import java.util.UUID;

public record NotificationPreferenceResponseDTO(
        UUID userId,
        boolean emailEnabled,
        boolean pushEnabled) {
}
