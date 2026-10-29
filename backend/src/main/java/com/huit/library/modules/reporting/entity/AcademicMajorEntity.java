package com.huit.library.modules.reporting.entity;
import jakarta.persistence.*;import lombok.Data;
@Data @Entity @Table(name="academic_majors",uniqueConstraints=@UniqueConstraint(columnNames={"faculty_id","major_name"}))
public class AcademicMajorEntity {
    @Id @Column(length=128) private String majorId;
    @Column(name="faculty_id",nullable=false,length=64) private String facultyId;
    @Column(name="major_name",nullable=false) private String majorName;
    @Column(length=32) private String majorCode;
    @Column(nullable=false,length=1000) private String sourceReference;
}
