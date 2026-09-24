package com.huit.library.modules.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/books")
@Tag(name = "3. Catalog & Search", description = "Endpoints for books, categories, and AI search")
public class BookController {

    @GetMapping
    @Operation(summary = "Search Books", description = "Full-text fuzzy search (Hits Elasticsearch).")
    public ResponseEntity<?> searchBooks(@RequestParam(required = false) String query) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Book Details", description = "Detailed view including ISBN and DDC call number.")
    public ResponseEntity<?> getBookById(@Parameter(description = "Book ID") @PathVariable String id) {
        return ResponseEntity.ok().build();
    }

    @GetMapping("/recommendations")
    @Operation(summary = "Get Recommendations", description = "Fetches personalized book lists (Calls Python FastAPI via Feign).")
    public ResponseEntity<?> getRecommendations() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/reviews")
    @Operation(summary = "Add Review", description = "Rate and review a specific book.")
    public ResponseEntity<?> addReview(@Parameter(description = "Book ID") @PathVariable String id) {
        return ResponseEntity.ok().build();
    }
}
