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
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "penalty_cases")
public class PenaltyCaseEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long caseId;
    private UUID userId;
    private Long ticketId;
    private Long loanDetailId;
    private String barcode;
    private String incidentType;
    private String damageSeverity;
    private Integer pagesAffected;
    private String description;
    private String evidenceUrl;
    private String materialType;
    private String languageCategory;
    private Boolean stillInMarket;
    private String settlementMethod;
    private String replacementBarcode;
    private LocalDateTime replacementAcceptedAt;
    private UUID replacementAcceptedBy;
    private String replacementVerificationNote;
    private BigDecimal coverPrice;
    private String originalCurrency;
    private BigDecimal fxRate;
    private String fxSource;
    private BigDecimal multiplier;
    private BigDecimal repairFee = BigDecimal.ZERO;
    private BigDecimal compensationAmount = BigDecimal.ZERO;
    private BigDecimal technicalFee = BigDecimal.ZERO;
    private BigDecimal overdueFineSnapshot = BigDecimal.ZERO;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount = BigDecimal.ZERO;
    private String status;
    private LocalDateTime reportedAt;
    private LocalDateTime settlementDueAt;
    private UUID createdBy;
    private UUID decisionBy;
    private String externalDecisionMaker;
    private String decisionDocumentNo;
    private LocalDate decisionDate;
    private BigDecimal waiverAmount = BigDecimal.ZERO;
    private String decisionReason;
}
