package com.huit.library.modules.orientation.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "library_orientation_enrollments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"class_id", "user_id"}))
public class OrientationEnrollmentEntity extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long enrollmentId;
    private Long classId;
    private UUID userId;
    private String attendanceStatus;
    private String note;
    private UUID markedBy;
    private LocalDateTime markedAt;
}
