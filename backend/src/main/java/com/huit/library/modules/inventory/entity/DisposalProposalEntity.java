package com.huit.library.modules.inventory.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.UUID;

@Data @EqualsAndHashCode(callSuper = true)
@Entity @Table(name = "disposal_proposals")
public class DisposalProposalEntity extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID proposalId;
    @Column(nullable = false) private UUID requestId;
    @Column(nullable = false) private String barcode;
    @Column(nullable = false) private Long bookId;
    @Column(nullable = false) private String bookTitle;
    @Column(nullable = false) private Long auditId;
    @Column(nullable = false) private String sourceDetailId;
    @Column(nullable = false, length = 64) private String sourceFingerprint;
    @Column(nullable = false, columnDefinition = "TEXT") private String sourceSnapshot;
    @Column(nullable = false, length = 1000) private String observedCondition;
    @Column(nullable = false, length = 20) private String reasonCode;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(nullable = false, length = 20) private String proposedAction;
    @Column(length = 1000) private String evidenceUrl;
    @Column(nullable = false, length = 20) private String status;
    @Version @Column(nullable = false) private Long version;
    @Column(nullable = false) private UUID createdBy;
    @Column(nullable = false) private String createdByName;
}
