package com.huit.library.modules.reporting.entity;
import jakarta.persistence.*;import lombok.Data;import java.util.UUID;import java.time.LocalDateTime;
@Data @Entity @Table(name="material_classification_history",uniqueConstraints=@UniqueConstraint(columnNames={"profile_id","version"}))
public class MaterialClassificationHistoryEntity {
    @Id private UUID eventId;
    @Column(name="profile_id",nullable=false) private UUID profileId;
    @Column(name="version",nullable=false) private Long version;
    @Column(nullable=false) private UUID actorId;
    @Column(nullable=false) private String actorName;
    @Column(nullable=false) private LocalDateTime recordedAt;
    @Column(columnDefinition="TEXT") private String beforeSnapshot;
    @Column(nullable=false,columnDefinition="TEXT") private String afterSnapshot;
}
