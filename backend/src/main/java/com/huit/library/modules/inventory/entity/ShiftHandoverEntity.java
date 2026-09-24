package com.huit.library.modules.inventory.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "shift_handovers")
public class ShiftHandoverEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long shiftId;

    private String posTerminalId;
    private BigDecimal startingCash;
    private BigDecimal systemRevenue;
    private BigDecimal actualCash;
    private BigDecimal discrepancyAmount;
    private String status;
}
