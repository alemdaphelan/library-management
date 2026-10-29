package com.huit.library.modules.notification.service;

import com.huit.library.modules.notification.dto.PushDeviceResponseDTO;
import com.huit.library.modules.notification.dto.RegisterPushDeviceRequestDTO;
import com.huit.library.modules.notification.entity.PushDeviceRegistrationEntity;
import com.huit.library.modules.notification.repository.PushDeviceRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class PushDeviceService {
    private final PushDeviceRegistrationRepository repository;

    public PushDeviceService(PushDeviceRegistrationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PushDeviceResponseDTO register(UUID userId, RegisterPushDeviceRequestDTO request) {
        PushDeviceRegistrationEntity registration = repository.findByToken(request.token().trim())
                .orElseGet(PushDeviceRegistrationEntity::new);
        registration.setUserId(userId);
        registration.setToken(request.token().trim());
        registration.setPlatform(request.platform().trim().toUpperCase());
        registration.setDeviceId(trimToNull(request.deviceId()));
        registration.setActive(true);
        registration.setLastSeenAt(LocalDateTime.now());
        return toResponse(repository.save(registration));
    }

    @Transactional
    public void unregister(UUID userId, String token) {
        repository.findByUserIdAndToken(userId, token.trim()).ifPresent(registration -> {
            registration.setActive(false);
            registration.setLastSeenAt(LocalDateTime.now());
            repository.save(registration);
        });
    }

    private PushDeviceResponseDTO toResponse(PushDeviceRegistrationEntity entity) {
        return new PushDeviceResponseDTO(
                entity.getRegistrationId(), entity.getPlatform(), entity.getDeviceId(),
                Boolean.TRUE.equals(entity.getActive()), entity.getLastSeenAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
