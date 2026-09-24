package com.huit.library.core.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "system_configs")
public class SystemConfigEntity extends BaseEntity {
    @Id
    private String configKey;

    private String configValue;
    private String description;
}
