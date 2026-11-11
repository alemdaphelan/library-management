package com.huit.library.modules.notification.service;

import com.huit.library.modules.notification.dto.PushDeviceResponseDTO;
import com.huit.library.modules.notification.dto.RegisterPushDeviceRequestDTO;
import com.huit.library.modules.notification.entity.PushDeviceRegistrationEntity;
import com.huit.library.modules.notification.repository.PushDeviceRegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class PushDeviceService {
    private final PushDeviceRegistrationRepository repository;

    public PushDeviceService(PushDeviceRegistrationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public PushDeviceResponseDTO register(UUID userId, RegisterPushDeviceRequestDTO request) {
        String normalizedToken = request.token().trim();
        String normalizedPlatform = request.platform().trim().toUpperCase();
        String normalizedDeviceId = trimToNull(request.deviceId());
        LocalDateTime now = LocalDateTime.now();

        Optional<PushDeviceRegistrationEntity> existingByToken =
                repository.findByToken(normalizedToken);
        PushDeviceRegistrationEntity registration = existingByToken.orElse(null);

        if (normalizedDeviceId != null) {
            List<PushDeviceRegistrationEntity> activeOnSameDevice =
                    repository.findByDeviceIdAndActiveTrue(normalizedDeviceId);
            if (activeOnSameDevice != null) {
                for (PushDeviceRegistrationEntity other : activeOnSameDevice) {
                    if (registration == null && Objects.equals(other.getUserId(), userId)) {
                        registration = other;
                        continue;
                    }
                    if (registration == null
                            || !Objects.equals(
                                    other.getRegistrationId(), registration.getRegistrationId())) {
                        other.setActive(false);
                        other.setLastSeenAt(now);
                        repository.save(other);
                    }
                }
            }
        }

        if (registration == null) {
            registration = new PushDeviceRegistrationEntity();
        }
        registration.setUserId(userId);
        registration.setToken(normalizedToken);
        registration.setIdentifierType(
                request.identifierType() == null ? "TOKEN" : request.identifierType());
        registration.setPlatform(normalizedPlatform);
        registration.setDeviceId(normalizedDeviceId);
        registration.setActive(true);
        registration.setLastSeenAt(now);
        return toResponse(repository.save(registration));
    }

    @Transactional
    public void unregister(UUID userId, String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        repository
                .findByUserIdAndToken(userId, token.trim())
                .ifPresent(
                        registration -> {
                            registration.setActive(false);
                            registration.setLastSeenAt(LocalDateTime.now());
                            repository.save(registration);
                        });
    }

    private PushDeviceResponseDTO toResponse(PushDeviceRegistrationEntity entity) {
        return new PushDeviceResponseDTO(
                entity.getRegistrationId(),
                entity.getPlatform(),
                entity.getDeviceId(),
                Boolean.TRUE.equals(entity.getActive()),
                entity.getLastSeenAt());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
