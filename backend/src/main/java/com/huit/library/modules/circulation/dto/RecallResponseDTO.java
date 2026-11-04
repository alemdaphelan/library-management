package com.huit.library.modules.circulation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecallResponseDTO {
    private Long detailId;
    private Long ticketId;
    private LocalDateTime recalledAt;
    private LocalDateTime recallDueAt;
    private LocalDateTime dueDate;
    private String status;
}
