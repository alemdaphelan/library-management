package com.huit.library.modules.notification.repository;

import com.huit.library.modules.notification.entity.PushDeviceRegistrationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRegistrationRepository
        extends JpaRepository<PushDeviceRegistrationEntity, UUID> {
    Optional<PushDeviceRegistrationEntity> findByToken(String token);

    Optional<PushDeviceRegistrationEntity> findByUserIdAndToken(UUID userId, String token);

    List<PushDeviceRegistrationEntity> findByUserIdAndActiveTrue(UUID userId);
}
