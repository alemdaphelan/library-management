package com.huit.library.modules.inventory.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.UUID;

@Data @EqualsAndHashCode(callSuper = true)
@Entity @Table(name = "disposal_batches")
public class DisposalBatchEntity extends BaseEntity {
    @Id private UUID requestId;
    @Column(nullable = false) private UUID createdBy;
    @Column(nullable = false, columnDefinition = "TEXT") private String requestPayload;
}
