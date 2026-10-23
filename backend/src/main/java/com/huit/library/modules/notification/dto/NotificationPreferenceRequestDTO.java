package com.huit.library.modules.notification.dto;

import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceRequestDTO(
        @NotNull Boolean emailEnabled,
        @NotNull Boolean pushEnabled) {
}
