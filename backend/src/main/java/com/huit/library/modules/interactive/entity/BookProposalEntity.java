package com.huit.library.modules.interactive.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;
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
    private String publisher;
    private String isbn;
    private Integer publicationYear;
    private String edition;
    @Column(precision = 15, scale = 2)
    private BigDecimal estimatedPrice;
    @Column(length = 1000)
    private String externalUrl;
    private String genre;
    private String urgencyLevel;
    @Column(length = 1000)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String note;

    @Column(columnDefinition = "TEXT")
    private String adminNote;
    private Long approvedBookId;
    private String status; // PENDING, APPROVED, REJECTED
}
