package com.huit.library.modules.inventory.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.UUID;

@Data @EqualsAndHashCode(callSuper = true)
@Entity @Table(name = "disposal_history", uniqueConstraints = @UniqueConstraint(columnNames = {"proposal_id", "proposal_version"}))
public class DisposalHistoryEntity extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID eventId;
    @Column(name = "proposal_id", nullable = false) private UUID proposalId;
    @Column(name = "proposal_version", nullable = false) private Long proposalVersion;
    @Column(nullable = false, length = 20) private String eventType;
    @Column(nullable = false) private UUID actorId;
    @Column(nullable = false) private String actorName;
    @Column(columnDefinition = "TEXT") private String beforeSnapshot;
    @Column(nullable = false, columnDefinition = "TEXT") private String afterSnapshot;
}
