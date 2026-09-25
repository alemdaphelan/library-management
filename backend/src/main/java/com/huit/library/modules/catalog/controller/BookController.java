package com.huit.library.modules.catalog.controller;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/books")
@Tag(name = "3. Catalog & Search", description = "Endpoints for books, categories, and AI search")
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    @GetMapping
    @Operation(summary = "Search Books", description = "Full-text fuzzy search (Hits Elasticsearch).")
    public ResponseEntity<?> searchBooks(@RequestParam(required = false) String query) {
        return ResponseEntity.ok(bookService.searchBooks(query));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Book Details", description = "Detailed view including ISBN and DDC call number.")
    public ResponseEntity<?> getBookById(@Parameter(description = "Book ID") @PathVariable Long id) {
        return ResponseEntity.ok(bookService.getBookById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Create Book")
    public ResponseEntity<?> createBook(@RequestBody BookEntity book) {
        return ResponseEntity.ok(bookService.createBook(book));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Update Book")
    public ResponseEntity<?> updateBook(@PathVariable Long id, @RequestBody BookEntity book) {
        return ResponseEntity.ok(bookService.updateBook(id, book));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Delete Book")
    public ResponseEntity<?> deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return ResponseEntity.ok("Book deleted successfully");
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Get Recommendations", description = "Fetches personalized book lists (Calls Python FastAPI via Feign).")
    public ResponseEntity<?> getRecommendations() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reviews")
    @Operation(summary = "Add Review", description = "Rate and review a specific book.")
    public ResponseEntity<?> addReview(@Parameter(description = "Book ID") @PathVariable Long id) {
        return ResponseEntity.ok().build();
    }
}
