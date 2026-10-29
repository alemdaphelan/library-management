package com.huit.library.modules.reporting.entity;
import jakarta.persistence.*;import lombok.Data;import java.util.UUID;import java.time.LocalDateTime;
@Data @Entity @Table(name="material_classifications",uniqueConstraints=@UniqueConstraint(columnNames={"material_kind","material_id"}))
public class MaterialClassificationEntity {
    @Id private UUID profileId;
    @Column(name="material_kind",nullable=false,length=16) private String materialKind;
    @Column(name="material_id",nullable=false) private Long materialId;
    @Column(nullable=false) private Long version;
    @Column(nullable=false,length=1000) private String sourceReference;
    @Column(nullable=false) private UUID updatedBy;
    @Column(nullable=false) private String updatedByName;
    @Column(nullable=false) private LocalDateTime updatedAt;
}
