package com.huit.library.modules.notification.service;

import com.huit.library.modules.notification.dto.NotificationPreferenceRequestDTO;
import com.huit.library.modules.notification.dto.NotificationPreferenceResponseDTO;
import com.huit.library.modules.notification.entity.NotificationPreferenceEntity;
import com.huit.library.modules.notification.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationPreferenceService {
    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceService(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public NotificationPreferenceResponseDTO get(UUID userId) {
        return repository.findById(userId)
                .map(this::toResponse)
                .orElseGet(() -> new NotificationPreferenceResponseDTO(userId, true, true));
    }

    @Transactional
    public NotificationPreferenceResponseDTO update(
            UUID userId, NotificationPreferenceRequestDTO request) {
        NotificationPreferenceEntity preference = repository.findById(userId)
                .orElseGet(() -> {
                    NotificationPreferenceEntity created = new NotificationPreferenceEntity();
                    created.setUserId(userId);
                    return created;
                });
        preference.setEmailEnabled(request.emailEnabled());
        preference.setPushEnabled(request.pushEnabled());
        return toResponse(repository.save(preference));
    }

    @Transactional(readOnly = true)
    public boolean isEmailEnabled(UUID userId) {
        return repository.findById(userId)
                .map(NotificationPreferenceEntity::getEmailEnabled)
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean isPushEnabled(UUID userId) {
        return repository.findById(userId)
                .map(NotificationPreferenceEntity::getPushEnabled)
                .orElse(true);
    }

    private NotificationPreferenceResponseDTO toResponse(NotificationPreferenceEntity entity) {
        return new NotificationPreferenceResponseDTO(
                entity.getUserId(),
                Boolean.TRUE.equals(entity.getEmailEnabled()),
                Boolean.TRUE.equals(entity.getPushEnabled()));
    }
}
