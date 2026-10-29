package com.huit.library.modules.orientation.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class OrientationDtos {
    private OrientationDtos() {}

    public record ClassRequest(
            @NotBlank String title,
            @NotBlank String location,
            @NotNull LocalDateTime startAt,
            @NotNull LocalDateTime endAt,
            @Min(1) @Max(30) Integer capacity,
            UUID facilitatorId) {}

    public record AssignmentRequest(List<UUID> userIds, @Min(0) @Max(30) Integer randomCount) {}
    public record AttendanceItem(@NotNull UUID userId, @NotBlank String status, String note) {}
    public record AttendanceRequest(@NotEmpty List<AttendanceItem> items) {}
    public record Person(UUID userId, String fullName, String mssv, String email) {}
    public record Enrollment(Long enrollmentId, Person student, String status, String note) {}
    public record ClassResponse(
            Long classId, String classCode, String title, String location,
            LocalDateTime startAt, LocalDateTime endAt, Integer capacity,
            UUID facilitatorId, String facilitatorName, String status,
            int enrolledCount, List<Enrollment> enrollments) {}
}
