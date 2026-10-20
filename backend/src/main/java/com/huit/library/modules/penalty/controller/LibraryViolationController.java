package com.huit.library.modules.penalty.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.penalty.dto.LibraryViolationRequestDTO;
import com.huit.library.modules.penalty.service.LibraryViolationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/library-violations")
@PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
public class LibraryViolationController {
    private final LibraryViolationService violationService;

    public LibraryViolationController(LibraryViolationService violationService) {
        this.violationService = violationService;
    }

    @PostMapping
    public ResponseEntity<?> record(
            @Valid @RequestBody LibraryViolationRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails operator) {
        return ResponseEntity.ok(violationService.record(
                request, operator.getUser().getUserId()));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getByUser(@PathVariable UUID userId) {
        return ResponseEntity.ok(violationService.getByUser(userId));
    }
}
