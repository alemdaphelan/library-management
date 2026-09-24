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
@Table(name = "users")
public class UserEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID userId;

    private String roleId;
    private String passwordHash;
    private Boolean isFirstLogin;
    private String fullName;
    private String studentId;
    private String department;
    private String email;
    private String phone;
    private String userType;
    private String cardStatus;
    private LocalDateTime cardExpiryDate;
    private Boolean disciplineFlag;
    private Integer roomViolationCount = 0;
}
