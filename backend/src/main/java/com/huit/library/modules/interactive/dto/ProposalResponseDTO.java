package com.huit.library.modules.interactive.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class ProposalResponseDTO {
    private Long proposalId;
    private UUID userId;
    private String userName;
    private String userMssv;
    private String userEmail;
    private String title;
    private String authorName;
    private String publisher;
    private String reason;
    private String note;
    private String adminNote;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
