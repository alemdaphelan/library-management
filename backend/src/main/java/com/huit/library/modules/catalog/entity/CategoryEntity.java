package com.huit.library.modules.catalog.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "categories")
public class CategoryEntity extends BaseEntity {
    public static final String GENERAL_EDUCATION = "Giáo dục đại cương";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;

    private String name;
    private String description;
}
