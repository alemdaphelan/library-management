package com.huit.library.modules.circulation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "circulation_operations")
public class CirculationOperationEntity {
    @Id
    private UUID operationId;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 16)
    private String operationType;

    @Column(nullable = false, length = 64)
    private String requestHash;

    @Column(nullable = false, length = 1000)
    private String ticketIds;

    @Column(nullable = false, length = 4000)
    private String barcodes;

    @Column(nullable = false, length = 32)
    private String resultStatus;

    @Column(nullable = false, length = 500)
    private String message;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime processedAt;
}
