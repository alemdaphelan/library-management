package com.huit.library.modules.notification.service;

import com.huit.library.modules.notification.entity.NotificationEntity;
import com.huit.library.modules.notification.repository.NotificationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;

    public NotificationService(NotificationRepository notificationRepository,
                               ApplicationEventPublisher eventPublisher) {
        this.notificationRepository = notificationRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public boolean createOnce(
            UUID userId,
            String type,
            String content,
            String referenceType,
            String referenceId,
            String dedupKey,
            LocalDateTime sentAt) {
        boolean created = notificationRepository.insertIfAbsent(
                UUID.randomUUID(), userId, type, content, sentAt,
                referenceType, referenceId, dedupKey) == 1;
        if (created) {
            eventPublisher.publishEvent(new PushNotificationEvent(
                    userId, type, content, referenceType, referenceId));
        }
        return created;
    }

    @Transactional(readOnly = true)
    public List<NotificationEntity> getMyNotifications(UUID userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public NotificationEntity markAsRead(UUID notificationId, UUID userId) {
        NotificationEntity notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"));
        if (!userId.equals(notification.getUserId())) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Bạn không có quyền cập nhật thông báo này");
        }
        notification.setIsRead(true);
        return notificationRepository.save(notification);
    }
}
