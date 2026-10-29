package com.huit.library.modules.notification.repository;

import com.huit.library.modules.notification.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    List<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @Modifying
    @Query(value = """
            INSERT INTO notifications (
                notif_id, user_id, type, content, is_read, sent_at,
                reference_type, reference_id, dedup_key, created_at, updated_at)
            VALUES (
                :notificationId, :userId, :type, :content, false, :sentAt,
                :referenceType, :referenceId, :dedupKey, :sentAt, :sentAt)
            ON CONFLICT (dedup_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("notificationId") UUID notificationId,
            @Param("userId") UUID userId,
            @Param("type") String type,
            @Param("content") String content,
            @Param("sentAt") LocalDateTime sentAt,
            @Param("referenceType") String referenceType,
            @Param("referenceId") String referenceId,
            @Param("dedupKey") String dedupKey);
}
