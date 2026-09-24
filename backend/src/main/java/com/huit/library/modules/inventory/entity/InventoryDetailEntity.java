package com.huit.library.modules.inventory.entity;

import java.util.UUID;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "inventory_details")
public class InventoryDetailEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String detailId;

    private String auditId;
    private String barcode;
    private String scanStatus;
    private String scannedLocation;
}
