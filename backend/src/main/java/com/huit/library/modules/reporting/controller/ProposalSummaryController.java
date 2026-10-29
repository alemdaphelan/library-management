package com.huit.library.modules.reporting.controller;

import com.huit.library.modules.reporting.dto.ProposalSummaryReport;
import com.huit.library.modules.reporting.dto.ProposalSummaryReport.Status;
import com.huit.library.modules.reporting.dto.StudentObligationReport.Faculty;
import com.huit.library.modules.reporting.service.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/proposals/admin/summary")
@PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
public class ProposalSummaryController {
    private final ProposalSummaryService service;
    private final ProposalSummaryExcelExporter exporter;
    private final StudentObligationReportService academic;
    public ProposalSummaryController(ProposalSummaryService service, ProposalSummaryExcelExporter exporter, StudentObligationReportService academic) {
        this.service = service; this.exporter = exporter; this.academic = academic;
    }
    @GetMapping("/faculties")
    public ResponseEntity<List<Faculty>> faculties() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(academic.faculties()); }
    @GetMapping
    public ResponseEntity<ProposalSummaryReport> preview(@RequestParam(required = false) String facultyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ALL") Status status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.preview(facultyId, from, to, status, page, size));
    }
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) String facultyId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "ALL") Status status) throws IOException {
        var report = service.exportData(facultyId, from, to, status);
        String filename = "de-xuat-mua-" + report.snapshotAt().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".xlsx";
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(exporter.export(report));
    }
}
