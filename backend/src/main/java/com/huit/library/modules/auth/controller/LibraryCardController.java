package com.huit.library.modules.auth.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.auth.dto.LibraryCardResponseDTO;
import com.huit.library.modules.auth.service.LibraryCardService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/library-card")
public class LibraryCardController {
    private final LibraryCardService libraryCardService;

    public LibraryCardController(LibraryCardService libraryCardService) {
        this.libraryCardService = libraryCardService;
    }

    @GetMapping
    @Operation(summary = "Get current user's library card")
    public ResponseEntity<LibraryCardResponseDTO> get(
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(libraryCardService.get(principal.getUser().getUserId()));
    }
}
