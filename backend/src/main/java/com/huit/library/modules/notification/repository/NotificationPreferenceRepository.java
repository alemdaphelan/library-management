package com.huit.library.modules.notification.repository;

import com.huit.library.modules.notification.entity.NotificationPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface NotificationPreferenceRepository
        extends JpaRepository<NotificationPreferenceEntity, UUID> {
}
