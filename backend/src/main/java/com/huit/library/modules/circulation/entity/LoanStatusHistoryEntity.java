package com.huit.library.modules.circulation.entity;

import com.huit.library.modules.circulation.model.LoanTicketStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "loan_status_history")
public class LoanStatusHistoryEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    private Long ticketId;

    @Enumerated(EnumType.STRING)
    private LoanTicketStatus fromStatus;

    @Enumerated(EnumType.STRING)
    private LoanTicketStatus toStatus;

    private UUID changedBy;
    private String reason;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
