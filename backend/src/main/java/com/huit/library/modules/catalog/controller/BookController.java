package com.huit.library.modules.catalog.controller;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.dto.BookResponseDTO;
import com.huit.library.modules.catalog.service.BookService;
import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.recommendation.service.BookRecommendationService;
import com.huit.library.modules.recommendation.service.HybridRecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping("/books")
@Tag(name = "3. Catalog & Search", description = "Endpoints for books, categories, and AI search")
public class BookController {

    private final BookService bookService;
    private final BookRecommendationService recommendationService;
    private final HybridRecommendationService hybridRecommendationService;

    public BookController(
            BookService bookService,
            BookRecommendationService recommendationService,
            HybridRecommendationService hybridRecommendationService) {
        this.bookService = bookService;
        this.recommendationService = recommendationService;
        this.hybridRecommendationService = hybridRecommendationService;
    }
    @GetMapping
    @Operation(summary = "Search Books", description = "Search by text with optional category and HUIT major filters.")
    public ResponseEntity<?> searchBooks(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        org.springframework.data.domain.Page<BookResponseDTO> bookPage =
                bookService.searchBooks(query, categoryId, department, pageable);
        
        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("content", bookPage.getContent());
        response.put("totalPages", bookPage.getTotalPages());
        response.put("totalElements", bookPage.getTotalElements());
        response.put("size", bookPage.getSize());
        response.put("number", bookPage.getNumber());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/departments")
    @Operation(summary = "List Search Majors", description = "Lists configured HUIT majors available for catalog filtering.")
    public ResponseEntity<?> getSearchDepartments() {
        return ResponseEntity.ok(bookService.getSearchDepartments());
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

    @GetMapping({"/recommendations", "/recommendations/popular"})
    @Operation(summary = "Get Popularity Baseline", description = "Ranks recent loans with time decay and falls back to the catalog when history is sparse.")
    public ResponseEntity<?> getRecommendations(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "365") int windowDays) {
        return ResponseEntity.ok(recommendationService.getPopularBooks(limit, windowDays));
    }

    @GetMapping("/recommendations/personalized")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get Personalized Recommendations", description = "Hybrid ranking using borrowing history, department affinity, and popularity with cold-start support.")
    public ResponseEntity<?> getPersonalizedRecommendations(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(hybridRecommendationService.getPersonalizedRecommendations(
                principal.getUser().getUserId(), limit));
    }

    @GetMapping("/{id}/similar")
    @Operation(summary = "Get Similar Books", description = "Returns books ranked by TF-IDF and cosine similarity.")
    public ResponseEntity<?> getSimilarBooks(
            @Parameter(description = "Source book ID") @PathVariable Long id,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(recommendationService.getSimilarBooks(id, limit));
    }

    @PostMapping("/{id}/reviews")
    @Operation(summary = "Add Review", description = "Rate and review a specific book.")
    public ResponseEntity<?> addReview(@Parameter(description = "Book ID") @PathVariable Long id) {
        return ResponseEntity.ok().build();
    }
}
