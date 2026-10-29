package com.huit.library.modules.reporting.service;

import com.huit.library.modules.reporting.dto.StudentObligationReport;
import com.huit.library.modules.reporting.dto.StudentObligationReport.*;
import com.huit.library.modules.reporting.repository.StudentObligationReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
public class StudentObligationReportService {
    public static final String TIMEZONE = "Asia/Ho_Chi_Minh";
    private final StudentObligationReportRepository repository;

    public StudentObligationReportService(StudentObligationReportRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public List<Faculty> faculties() {
        var faculties = new ArrayList<>(repository.faculties());
        faculties.add(new Faculty("UNCLASSIFIED", "Chưa phân loại"));
        return List.copyOf(faculties);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ClassificationCoverage classificationCoverage() { return repository.classificationCoverage(); }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StudentObligationReport preview(Mode mode, String facultyId, String query, int page, int size) {
        if (page < 0 || size < 1 || size > 200) throw badRequest("page phải không âm; size từ 1 đến 200");
        return snapshot(filter(mode, facultyId, query), page, size);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StudentObligationReport exportData(Mode mode, String facultyId, String query) {
        return snapshot(filter(mode, facultyId, query), null, 0);
    }

    private StudentObligationReport snapshot(Filter filter, Integer page, int size) {
        LocalDateTime at = LocalDateTime.now(ZoneId.of(TIMEZONE));
        long count = repository.count(filter, at);
        var students = repository.students(filter, at, page, size);
        var ids = students.stream().map(Student::userId).toList();
        // Chunk IN parameters for large exports; all chunks share this read-only repeatable-read snapshot.
        var overdue = new ArrayList<OverdueItem>();
        var ledger = new ArrayList<LedgerEntry>();
        for (int start = 0; start < ids.size(); start += 500) {
            var chunk = ids.subList(start, Math.min(start + 500, ids.size()));
            overdue.addAll(repository.overdueItems(chunk, at));
            ledger.addAll(repository.ledgerEntries(chunk));
        }
        return new StudentObligationReport(at, TIMEZONE, "VND", filter, page == null ? 0 : page,
                page == null ? students.size() : size, count, students, overdue, ledger);
    }

    private Filter filter(Mode mode, String facultyId, String query) {
        String faculty = facultyId == null || facultyId.isBlank() ? null : facultyId.trim();
        String search = query == null ? "" : query.trim();
        if (search.length() > 100) throw badRequest("Từ khóa tối đa 100 ký tự");
        if (faculty != null && !"UNCLASSIFIED".equals(faculty)
                && repository.faculties().stream().noneMatch(item -> item.facultyId().equals(faculty))) {
            throw badRequest("facultyId không thuộc danh mục khoa đã ánh xạ");
        }
        return new Filter(mode == null ? Mode.EITHER : mode, faculty, search);
    }

    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
