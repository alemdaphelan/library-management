package com.huit.library.modules.digital.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.UUID;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "digital_documents")
public class DigitalDocumentEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long documentId;

    private Long bookId;
    private String fileUrl;
    private String format; // PDF, EPUB
    private Long fileSize; // In bytes
    private String originalFilename;
    private String displayName;
    private String contentType;
    private String storageKey;
    private String accessLevel;
    private UUID uploadedBy;
}
