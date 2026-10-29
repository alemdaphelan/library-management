package com.huit.library.modules.notification.service;

import java.util.UUID;

public record PushNotificationEvent(
        UUID userId,
        String type,
        String content,
        String referenceType,
        String referenceId) {
}
