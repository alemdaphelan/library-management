package com.huit.library.modules.digital.controller;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.digital.dto.DigitalAssetUpdateDTO;
import com.huit.library.modules.digital.service.DigitalAssetService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/digital-assets")
public class DigitalAssetController {
    private final DigitalAssetService digitalAssetService;

    public DigitalAssetController(DigitalAssetService digitalAssetService) {
        this.digitalAssetService = digitalAssetService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> list(Authentication authentication) {
        return ResponseEntity.ok(digitalAssetService.list(isStaff(authentication)));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "bookId", required = false) Long bookId,
            @RequestParam(value = "accessLevel", defaultValue = "AUTHENTICATED") String accessLevel,
            Authentication authentication) {
        return ResponseEntity.ok(digitalAssetService.upload(
                file, bookId, accessLevel, currentUserId(authentication)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public ResponseEntity<?> update(
            @PathVariable Long id,
            @Valid @RequestBody DigitalAssetUpdateDTO request) {
        return ResponseEntity.ok(digitalAssetService.update(id, request));
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> download(@PathVariable Long id, Authentication authentication) {
        DigitalAssetService.DigitalDownload download = digitalAssetService.download(
                id, currentUserId(authentication), isStaff(authentication));
        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(download.contentType());
        } catch (Exception ignored) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.filename(), StandardCharsets.UTF_8)
                        .build().toString());
        if (download.fileSize() != null) {
            response.contentLength(download.fileSize());
        }
        return response.body(download.resource());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        digitalAssetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof CustomUserDetails details) {
            return details.getUser().getUserId();
        }
        return null;
    }

    private boolean isStaff(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_LIBRARIAN".equals(authority.getAuthority()));
    }
}
