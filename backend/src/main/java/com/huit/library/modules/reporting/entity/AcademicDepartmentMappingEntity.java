package com.huit.library.modules.reporting.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "academic_department_mappings")
public class AcademicDepartmentMappingEntity {
    @Id
    private String departmentValue;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faculty_id", nullable = false)
    private AcademicFacultyEntity faculty;
    private String majorName;
}
