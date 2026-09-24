package com.huit.library.integration.sync.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "university_sync_logs")
public class UniversitySyncLogEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID logId;

    private String endpoint;
    @Column(columnDefinition = "json")
    private String payload;
    private String syncStatus;
    private LocalDateTime executedAt;
}
