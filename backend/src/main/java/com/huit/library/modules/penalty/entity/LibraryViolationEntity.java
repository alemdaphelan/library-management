package com.huit.library.modules.penalty.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "library_violations")
public class LibraryViolationEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long violationId;
    private UUID userId;
    private Long ticketId;
    private String barcode;
    private String violationType;
    private String description;
    private String actionTaken;
    private UUID recordedBy;
    private LocalDateTime occurredAt;
}
