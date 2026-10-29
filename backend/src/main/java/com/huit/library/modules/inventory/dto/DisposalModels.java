package com.huit.library.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.*;

public final class DisposalModels {
    private DisposalModels() {}
    public enum Status { DRAFT, SUBMITTED, CANCELLED }
    public enum ReasonCode { WORN, OBSOLETE, UNUSED, DAMAGED }
    public enum Action { REPAIR, TRANSFER, LIQUIDATE, DESTROY }
    public record Source(Long auditId, String auditStatus, LocalDateTime auditUpdatedAt, String detailId,
            String scanStatus, String scannedLocation, LocalDateTime scannedAt, LocalDateTime detailUpdatedAt,
            String barcode, Long bookId, String bookTitle, String isbn, String copyStatus, String conditionNote,
            Long shelfId, String location, LocalDateTime copyUpdatedAt, UUID lockedByUser, LocalDateTime lockedUntil) {}
    public record Candidate(Source source, String fingerprint, boolean eligible, List<String> blockers, UUID activeProposalId) {}
    public record Line(@NotBlank @Size(max = 255) String barcode, @NotBlank @Size(max = 255) String sourceDetailId,
            @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String expectedFingerprint,
            @NotBlank @Size(max = 1000) String observedCondition, @NotNull ReasonCode reasonCode,
            @NotBlank @Size(max = 1000) String reason, @NotNull Action proposedAction, @Size(max = 1000) String evidenceUrl) {}
    public record Create(@NotNull UUID requestId, @NotNull @Positive Long auditId,
            @NotEmpty @Size(max = 50) List<@NotNull @Valid Line> lines) {}
    public record Edit(@NotNull @PositiveOrZero Long version, @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String expectedFingerprint,
            @NotBlank @Size(max = 1000) String observedCondition, @NotNull ReasonCode reasonCode,
            @NotBlank @Size(max = 1000) String reason, @NotNull Action proposedAction, @Size(max = 1000) String evidenceUrl) {}
    public record Transition(@NotNull @PositiveOrZero Long version) {}
    public record Snapshot(String status, long version, String observedCondition, String reasonCode, String reason,
            String proposedAction, String evidenceUrl, Source source) {}
    public record History(UUID eventId, long version, String eventType, UUID actorId, String actorName,
            LocalDateTime at, Snapshot before, Snapshot after) {}
    public record Item(UUID proposalId, UUID requestId, String barcode, Long bookId, String bookTitle, Long auditId,
            String status, long version, String proposedAction, UUID createdBy, String createdByName,
            LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record Detail(Item item, Snapshot saved, Candidate current, boolean canEdit, boolean canSubmit, boolean canCancel,
            String adminIntegrationStatus, List<History> history) {}
    public record Batch(UUID requestId, boolean replay, List<Item> proposals) {}
    public record PageResult(List<Item> items, long total, int page, int size) {}
}
