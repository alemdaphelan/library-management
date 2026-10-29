package com.huit.library.modules.penalty.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class LibraryViolationRequestDTO {
    @NotNull
    private UUID userId;
    private Long ticketId;
    @Size(max = 255)
    private String barcode;
    @NotBlank
    private String violationType;
    @NotBlank
    @Size(max = 1000)
    private String description;
    @Size(max = 1000)
    private String actionTaken;
    private LocalDateTime occurredAt;
}
