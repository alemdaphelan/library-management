package com.huit.library.modules.inventory.controller;
import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.inventory.dto.DisposalModels.*;
import com.huit.library.modules.inventory.service.DisposalService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/inventory/disposals")
@PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
public class DisposalController {
    private final DisposalService service;
    public DisposalController(DisposalService service) { this.service = service; }
    @GetMapping("/candidates") public ResponseEntity<?> candidates(@RequestParam Long auditId) { return ok(service.candidates(auditId)); }
    @GetMapping public ResponseEntity<?> list(@RequestParam(required = false) Status status, @RequestParam(required = false) Long auditId,
            @RequestParam(defaultValue = "") String barcode, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) { return ok(service.list(status, auditId, barcode, page, size)); }
    @PostMapping public ResponseEntity<?> create(@Valid @RequestBody Create request, @AuthenticationPrincipal CustomUserDetails user) { return ok(service.create(request, user == null ? null : user.getUser())); }
    @GetMapping("/{id}") public ResponseEntity<?> get(@PathVariable UUID id, @AuthenticationPrincipal CustomUserDetails user) { return ok(service.get(id, user == null ? null : user.getUser())); }
    @PutMapping("/{id}") public ResponseEntity<?> edit(@PathVariable UUID id, @Valid @RequestBody Edit request, @AuthenticationPrincipal CustomUserDetails user) { return ok(service.edit(id, request, user == null ? null : user.getUser())); }
    @PostMapping("/{id}/submit") public ResponseEntity<?> submit(@PathVariable UUID id, @Valid @RequestBody Transition request, @AuthenticationPrincipal CustomUserDetails user) { return ok(service.transition(id, request, true, user == null ? null : user.getUser())); }
    @PostMapping("/{id}/cancel") public ResponseEntity<?> cancel(@PathVariable UUID id, @Valid @RequestBody Transition request, @AuthenticationPrincipal CustomUserDetails user) { return ok(service.transition(id, request, false, user == null ? null : user.getUser())); }
    private ResponseEntity<?> ok(Object body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
