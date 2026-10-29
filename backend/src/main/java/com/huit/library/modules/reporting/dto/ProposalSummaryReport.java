package com.huit.library.modules.reporting.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ProposalSummaryReport(LocalDateTime snapshotAt, String timezone, Filter filter,
        int page, int size, long totalGroups, long totalProposals, long totalPeople, List<Group> groups) {
    public enum Status { ALL, PENDING, APPROVED, REJECTED }
    public record Filter(String facultyId, LocalDate from, LocalDate to, Status status) {}
    public record Source(Long proposalId, UUID userId, String userName, String mssv, String rawDepartment,
            String facultyId, String facultyName, String title, String authorName, String publisher,
            String isbn, Integer publicationYear, String edition, String status, LocalDateTime createdAt) {}
    public record FacultyCount(String facultyId, String facultyName, long proposalCount, long peopleCount) {}
    public record Group(String groupKey, String identityType, String canonicalIsbn, String title,
            String authorName, String publisher, Integer publicationYear, String edition,
            long proposalCount, long peopleCount, List<FacultyCount> faculties, List<String> warnings, List<Source> sources) {}
}
