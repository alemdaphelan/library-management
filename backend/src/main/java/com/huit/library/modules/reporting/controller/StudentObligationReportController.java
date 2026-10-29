package com.huit.library.modules.reporting.controller;

import com.huit.library.modules.reporting.dto.StudentObligationReport;
import com.huit.library.modules.reporting.dto.StudentObligationReport.*;
import com.huit.library.modules.reporting.service.StudentObligationExcelExporter;
import com.huit.library.modules.reporting.service.StudentObligationReportService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/librarian-reports/student-obligations")
@PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
public class StudentObligationReportController {
    private final StudentObligationReportService service;
    private final StudentObligationExcelExporter exporter;

    public StudentObligationReportController(StudentObligationReportService service, StudentObligationExcelExporter exporter) {
        this.service = service; this.exporter = exporter;
    }

    @GetMapping("/faculties")
    public ResponseEntity<List<Faculty>> faculties() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.faculties());
    }

    @GetMapping("/classification-coverage")
    public ResponseEntity<ClassificationCoverage> classificationCoverage() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.classificationCoverage());
    }

    @GetMapping
    public ResponseEntity<StudentObligationReport> preview(
            @RequestParam(defaultValue = "EITHER") Mode mode,
            @RequestParam(required = false) String facultyId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preview(mode, facultyId, q, page, size));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "EITHER") Mode mode,
            @RequestParam(required = false) String facultyId,
            @RequestParam(defaultValue = "") String q) throws IOException {
        var report = service.exportData(mode, facultyId, q);
        String filename = "thu-thu-" + report.filter().mode().name().toLowerCase(java.util.Locale.ROOT) + "-"
                + report.snapshotAt().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".xlsx";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(exporter.export(report));
    }
}
