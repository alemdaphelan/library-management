package com.huit.library.modules.penalty.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "compensation_rates")
public class CompensationRateEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long rateId;
    private String materialType;
    private String languageCategory;
    private BigDecimal amount;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private Boolean active = true;
    private UUID configuredBy;
    private String note;
}
