package com.huit.library.modules.auth.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "orientation_attendance")
public class OrientationAttendanceEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID recordId;

    private UUID userId;
    private LocalDateTime attendedDate;
    private Boolean isPassed;
    private Boolean processed;
}
