package com.huit.library.modules.inventory.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "inventory_audits")
public class InventoryAuditEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long auditId;

    private UUID conductedBy;
    private Integer totalSystemBooks;
    private Integer totalActualBooks;
    private String status;
}
