package com.huit.library.modules.finance.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "online_payment_sessions")
public class OnlinePaymentSessionEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentSessionId;

    @Column(nullable = false, unique = true, length = 80)
    private String reference;
    @Column(nullable = false)
    private UUID userId;
    @Column(nullable = false, length = 30)
    private String provider;
    @Column(nullable = false, length = 30)
    private String purpose;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(nullable = false, length = 30)
    private String status;
    private String gatewayTransactionNo;
    private String responseCode;
    private Long paymentTransactionId;
    @Column(nullable = false)
    private LocalDateTime expiresAt;
    private LocalDateTime completedAt;
}
