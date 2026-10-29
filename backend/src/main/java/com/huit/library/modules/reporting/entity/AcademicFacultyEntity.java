package com.huit.library.modules.reporting.entity;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "academic_faculties")
public class AcademicFacultyEntity {
    @Id
    @Column(length = 64)
    private String facultyId;
    @Column(nullable = false)
    private String facultyName;
}
