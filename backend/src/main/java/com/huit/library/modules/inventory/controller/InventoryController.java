package com.huit.library.modules.inventory.controller;

import com.huit.library.modules.inventory.entity.GoodsReceiptEntity;
import com.huit.library.modules.inventory.entity.SupplierEntity;
import com.huit.library.modules.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory")
@Tag(name = "9. Inventory & POS", description = "Endpoints for physical library operations, POS and audits")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
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
    public ResponseEntity<?> importBooks(@RequestBody GoodsReceiptEntity receipt) {
        return ResponseEntity.ok(inventoryService.importBooks(receipt));
    }

    @GetMapping("/receipts")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Get all receipts")
    public ResponseEntity<?> getAllReceipts() {
        return ResponseEntity.ok(inventoryService.getAllReceipts());
    }

    @PostMapping("/shift-handover")
    @Operation(summary = "Shift Handover", description = "POS end-of-shift cash discrepancy logging.")
    public ResponseEntity<?> shiftHandover() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/audits")
    @Operation(summary = "Periodic Audits", description = "Scan barcodes for periodic inventory checks.")
    public ResponseEntity<?> performAudit() {
        return ResponseEntity.ok().build();
    }
}
