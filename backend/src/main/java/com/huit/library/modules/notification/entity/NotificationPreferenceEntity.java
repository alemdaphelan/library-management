package com.huit.library.modules.notification.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "notification_preferences")
public class NotificationPreferenceEntity extends BaseEntity {
    @Id
    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private Boolean emailEnabled = true;

    @Column(nullable = false)
    private Boolean pushEnabled = true;
}
