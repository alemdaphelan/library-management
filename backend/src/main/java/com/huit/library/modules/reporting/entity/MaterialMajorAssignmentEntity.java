package com.huit.library.modules.reporting.entity;
import jakarta.persistence.*;import lombok.Data;import java.util.UUID;
@Data @Entity @Table(name="material_major_assignments",uniqueConstraints=@UniqueConstraint(columnNames={"profile_id","major_id"}))
public class MaterialMajorAssignmentEntity {
    @Id private UUID assignmentId;
    @Column(name="profile_id",nullable=false) private UUID profileId;
    @Column(name="major_id",nullable=false,length=128) private String majorId;
}
