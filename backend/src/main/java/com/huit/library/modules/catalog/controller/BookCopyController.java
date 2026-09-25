package com.huit.library.modules.catalog.controller;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.service.BookCopyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/book-copies")
@Tag(name = "4. Book Copies (Location & Status)", description = "Endpoints for managing specific physical copies and their locations")
public class BookCopyController {

    private final BookCopyService bookCopyService;

    public BookCopyController(BookCopyService bookCopyService) {
        this.bookCopyService = bookCopyService;
    }

    @GetMapping("/book/{bookId}")
    @Operation(summary = "Get copies by Book ID", description = "View all physical copies of a book, including their shelf location.")
    public ResponseEntity<?> getCopiesByBookId(@PathVariable Long bookId) {
        return ResponseEntity.ok(bookCopyService.getCopiesByBookId(bookId));
    }

    @PutMapping("/{barcode}/location")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Update copy location", description = "Update the physical shelf/room location of a book copy.")
    public ResponseEntity<?> updateLocation(@PathVariable String barcode, @RequestParam String location) {
        return ResponseEntity.ok(bookCopyService.updateLocation(barcode, location));
    }

    @PutMapping("/{barcode}/status")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Update copy status", description = "Update the status (e.g. AVAILABLE, LOST, DAMAGED).")
    public ResponseEntity<?> updateStatus(@PathVariable String barcode, @RequestParam String status) {
        return ResponseEntity.ok(bookCopyService.updateStatus(barcode, status));
    }
}
