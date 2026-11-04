package com.huit.library.modules.notification.service;

import com.huit.library.modules.notification.entity.NotificationEntity;
import com.huit.library.modules.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {
    @Test
    void publishesPushOnlyWhenNotificationWasInserted() {
        NotificationRepository repository = mock(NotificationRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        NotificationService service = new NotificationService(repository, publisher);
        UUID userId = UUID.randomUUID();
        when(repository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        boolean created = service.createOnce(
                userId, "DUE_SOON", "Sắp đến hạn", "LOAN", "12",
                "due:12", LocalDateTime.now());

        assertThat(created).isTrue();
        verify(publisher).publishEvent(any(PushNotificationEvent.class));
    }

    @Test
    void doesNotPublishDuplicateNotification() {
        NotificationRepository repository = mock(NotificationRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        NotificationService service = new NotificationService(repository, publisher);
        when(repository.insertIfAbsent(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        boolean created = service.createOnce(
                UUID.randomUUID(), "DUE_SOON", "Sắp đến hạn", "LOAN", "12",
                "due:12", LocalDateTime.now());

        assertThat(created).isFalse();
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void marksSingleAndAllNotificationsAsRead() {
        NotificationRepository repository = mock(NotificationRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        NotificationService service = new NotificationService(repository, publisher);
        UUID userId = UUID.randomUUID();
        UUID notifId = UUID.randomUUID();
        NotificationEntity entity = new NotificationEntity();
        entity.setNotifId(notifId);
        entity.setUserId(userId);
        entity.setIsRead(false);

        when(repository.findById(notifId)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        when(repository.markAllAsReadByUserId(userId)).thenReturn(3);

        NotificationEntity updated = service.markAsRead(notifId, userId);
        int bulkCount = service.markAllAsRead(userId);

        assertThat(updated.getIsRead()).isTrue();
        assertThat(bulkCount).isEqualTo(3);
    }
}
