package com.huit.library.modules.inventory.controller;

import com.huit.library.modules.inventory.dto.ImportReceiptRequestDTO;
import com.huit.library.modules.inventory.dto.InventoryScanRequestDTO;
import com.huit.library.modules.inventory.entity.SupplierEntity;
import com.huit.library.modules.inventory.service.InventoryAuditService;
import com.huit.library.modules.inventory.service.InventoryService;
import com.huit.library.core.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/inventory")
@Tag(name = "9. Inventory & POS", description = "Endpoints for physical library operations, POS and audits")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryAuditService inventoryAuditService;

    public InventoryController(InventoryService inventoryService, InventoryAuditService inventoryAuditService) {
        this.inventoryService = inventoryService;
        this.inventoryAuditService = inventoryAuditService;
    }

    @GetMapping("/suppliers")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Get all suppliers")
    public ResponseEntity<?> getAllSuppliers() {
        return ResponseEntity.ok(inventoryService.getAllSuppliers());
    }

    @PostMapping("/suppliers")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Create supplier")
    public ResponseEntity<?> createSupplier(@RequestBody SupplierEntity supplier) {
        return ResponseEntity.ok(inventoryService.createSupplier(supplier));
    }

    @PostMapping("/receipts")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Import Books", description = "Import new books from Suppliers (Goods Receipts).")
    public ResponseEntity<?> importBooks(@Valid @RequestBody ImportReceiptRequestDTO request) {
        return ResponseEntity.ok(inventoryService.importBooks(request));
    }

    @GetMapping("/receipts")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Get all receipts")
    public ResponseEntity<?> getAllReceipts() {
        return ResponseEntity.ok(inventoryService.getAllReceipts());
    }

    @GetMapping("/receipts/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    public ResponseEntity<?> getReceipt(@PathVariable Long id) { return ResponseEntity.ok(inventoryService.getReceipt(id)); }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Get inventory import statistics")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(inventoryService.getStats());
    }

    @PostMapping("/shift-handover")
    @Operation(summary = "Shift Handover", description = "POS end-of-shift cash discrepancy logging.")
    public ResponseEntity<?> shiftHandover() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/audits")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Start inventory audit")
    public ResponseEntity<?> createAudit(@AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.ok(inventoryAuditService.createAudit(userDetails == null ? null : userDetails.getUser()));
    }

    @GetMapping("/audits")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Get inventory audit history")
    public ResponseEntity<?> getAudits() {
        return ResponseEntity.ok(inventoryAuditService.getAudits());
    }

    @GetMapping("/audits/{auditId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Get inventory audit details")
    public ResponseEntity<?> getAudit(@PathVariable Long auditId) {
        return ResponseEntity.ok(inventoryAuditService.getAudit(auditId));
    }

    @PostMapping("/audits/{auditId}/scans")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Scan a barcode in an inventory audit")
    public ResponseEntity<?> scan(@PathVariable Long auditId,
                                  @Valid @RequestBody InventoryScanRequestDTO request) {
        return ResponseEntity.ok(inventoryAuditService.scan(auditId, request));
    }

    @PostMapping("/audits/{auditId}/complete")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Complete inventory audit")
    public ResponseEntity<?> completeAudit(@PathVariable Long auditId) {
        return ResponseEntity.ok(inventoryAuditService.complete(auditId));
    }
}
