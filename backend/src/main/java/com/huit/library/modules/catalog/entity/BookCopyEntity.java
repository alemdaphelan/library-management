package com.huit.library.modules.catalog.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "book_copies")
public class BookCopyEntity extends BaseEntity {
    @Id
    private String barcode;

    private Long bookId;
    private String conditionNote;
    
    private UUID lockedByUser;
    private LocalDateTime lockedUntil;
    private String location;
    private String status;
}
