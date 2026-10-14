package com.huit.library.modules.interactive.dto;

import lombok.Data;

@Data
public class CreateProposalRequestDTO {
    private String title;
    private String authorName;
    private String publisher;
    private String reason;
    private String note;
}
