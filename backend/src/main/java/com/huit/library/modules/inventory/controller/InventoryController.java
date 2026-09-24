package com.huit.library.modules.inventory.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/inventory")
@Tag(name = "9. Inventory & POS", description = "Endpoints for physical library operations, POS and audits")
public class InventoryController {

    @PostMapping("/shift-handover")
    @Operation(summary = "Shift Handover", description = "POS end-of-shift cash discrepancy logging.")
    public ResponseEntity<?> shiftHandover() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/receipts")
    @Operation(summary = "Import Books", description = "Import new books from Suppliers (Goods Receipts).")
    public ResponseEntity<?> importBooks() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/audits")
    @Operation(summary = "Periodic Audits", description = "Scan barcodes for periodic inventory checks.")
    public ResponseEntity<?> performAudit() {
        return ResponseEntity.ok().build();
    }
}
