package com.huit.library.modules.interactive.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "book_proposals")
public class BookProposalEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long proposalId;

    private UUID userId;
    private String title;
    private String authorName;
    private String reason;
    private String status; // PENDING, APPROVED, REJECTED
}
