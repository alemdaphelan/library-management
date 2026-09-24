package com.huit.library.modules.auth.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "staff_roles")
public class StaffRoleEntity extends BaseEntity {
    @Id
    private String roleId;

    private String roleName;
    @Column(columnDefinition = "json")
    private String permissions;
}
