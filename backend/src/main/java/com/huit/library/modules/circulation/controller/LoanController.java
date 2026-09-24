package com.huit.library.modules.circulation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/loans")
@Tag(name = "4. Circulation", description = "Endpoints for book borrowing and returning")
public class LoanController {

    @PostMapping("/checkout")
    @Operation(summary = "Borrow Books", description = "Process a new book checkout/borrow operation.")
    public ResponseEntity<?> checkoutBooks() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/return")
    @Operation(summary = "Return Books", description = "Process book returns and assess damage/penalties based on condition notes.")
    public ResponseEntity<?> returnBooks() {
        return ResponseEntity.ok().build();
    }
}