package com.huit.library.modules.orientation.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.orientation.dto.OrientationDtos;
import com.huit.library.modules.orientation.service.OrientationService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/orientation-classes")
@PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
public class OrientationController {
    private final OrientationService service;
    public OrientationController(OrientationService service) { this.service = service; }

    @GetMapping public Object list() { return service.list(); }
    @GetMapping("/{id}") public Object get(@PathVariable Long id) { return service.get(id); }
    @PostMapping @PreAuthorize("hasRole('ADMIN')")
    public Object create(@Valid @RequestBody OrientationDtos.ClassRequest request,
                                      @AuthenticationPrincipal CustomUserDetails principal) {
        return service.create(request, principal.getUser().getUserId());
    }
    @PutMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public Object update(@PathVariable Long id, @Valid @RequestBody OrientationDtos.ClassRequest request) { return service.update(id, request); }
    @DeleteMapping("/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) { service.delete(id); return ResponseEntity.noContent().build(); }
    @GetMapping("/{id}/available-students") @PreAuthorize("hasRole('ADMIN')")
    public Object students(@PathVariable Long id) { return service.availableStudents(id); }
    @GetMapping("/available-facilitators")
    public Object facilitators(@RequestParam LocalDateTime startAt, @RequestParam LocalDateTime endAt) { return service.availableFacilitators(startAt, endAt); }
    @PostMapping("/{id}/assign") @PreAuthorize("hasRole('ADMIN')")
    public Object assign(@PathVariable Long id, @Valid @RequestBody OrientationDtos.AssignmentRequest request) { return service.assign(id, request); }
    @DeleteMapping("/{id}/enrollments/{enrollmentId}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> unassign(@PathVariable Long id, @PathVariable Long enrollmentId) {
        service.unassign(id, enrollmentId);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{id}/attendance")
    public Object attendance(@PathVariable Long id, @Valid @RequestBody OrientationDtos.AttendanceRequest request,
                             @AuthenticationPrincipal CustomUserDetails principal) { return service.markAttendance(id, request, principal.getUser().getUserId()); }
    @GetMapping("/{id}/attendance-template")
    public ResponseEntity<byte[]> template(@PathVariable Long id) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=diem-danh-lop-" + id + ".xlsx")
                .body(service.attendanceTemplate(id));
    }
    @PostMapping(value = "/{id}/attendance-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Object importAttendance(@PathVariable Long id, @RequestParam MultipartFile file,
                                   @AuthenticationPrincipal CustomUserDetails principal) { return service.importAttendance(id, file, principal.getUser().getUserId()); }
}
