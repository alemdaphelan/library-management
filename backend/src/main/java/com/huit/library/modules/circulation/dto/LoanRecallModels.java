package com.huit.library.modules.circulation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class LoanRecallModels {
    private LoanRecallModels() {}

    public enum Filter {
        ALL,
        NOT_RECALLED,
        RECALLED,
        OVERDUE
    }

    public record Request(@NotBlank @Size(max = 1000) String reason) {}

    public record Item(
            Long ticketId,
            Long detailId,
            UUID userId,
            String mssv,
            String readerName,
            Long bookId,
            String bookTitle,
            String barcode,
            LocalDateTime dueDate,
            String itemStatus,
            boolean isRecalled,
            LocalDateTime recallRequestedAt,
            String recallReason,
            LocalDateTime recallDueDate,
            LocalDateTime lossReportedAt) {}

    public record Page(List<Item> items, long total, int page, int size) {}
}
