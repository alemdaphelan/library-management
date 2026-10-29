package com.huit.library.modules.auth.controller;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.dto.StudentUpsertRequestDTO;
import com.huit.library.modules.auth.service.UserService;
import com.huit.library.modules.auth.service.ReaderProfileService;
import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.modules.interactive.dto.WishlistRequestDTO;
import com.huit.library.modules.interactive.service.BookInteractionService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@Tag(name = "2. Users", description = "Endpoints for user management and synchronization")
public class UserController {

    private final UserService userService;
    private final BookInteractionService bookInteractionService;
    private final ReaderProfileService readerProfileService;

    public UserController(UserService userService, BookInteractionService bookInteractionService,
                          ReaderProfileService readerProfileService) {
        this.userService = userService;
        this.bookInteractionService = bookInteractionService;
        this.readerProfileService = readerProfileService;
    }

    @GetMapping("/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Get all students", description = "Retrieve list of all students.")
    public ResponseEntity<?> getAllStudents() {
        return ResponseEntity.ok(userService.getAllStudents());
    }

    @GetMapping("/students/{mssv}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Get student by MSSV")
    public ResponseEntity<?> getStudentByMssv(@PathVariable String mssv) {
        return ResponseEntity.ok(userService.getStudentByMssv(mssv));
    }

    @GetMapping("/students/{mssv}/profile")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Get complete reader profile", description = "Returns card, circulation, debt and penalty data for one student.")
    public ResponseEntity<?> getReaderProfile(@PathVariable String mssv) {
        return ResponseEntity.ok(readerProfileService.getByMssv(mssv));
    }

    @PostMapping("/students")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new student manually")
    public ResponseEntity<?> createStudent(@Valid @RequestBody StudentUpsertRequestDTO student) {
        return ResponseEntity.status(201).body(userService.createStudent(student));
    }

    @PutMapping("/students/{mssv}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update an existing student")
    public ResponseEntity<?> updateStudent(@PathVariable String mssv,
                                           @Valid @RequestBody StudentUpsertRequestDTO student) {
        return ResponseEntity.ok(userService.updateStudent(mssv, student));
    }

    @DeleteMapping("/students/{mssv}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a student")
    public ResponseEntity<?> deleteStudent(@PathVariable String mssv) {
        userService.deleteStudent(mssv);
        return ResponseEntity.noContent().build();
    }


    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Sync University Data", description = "Trigger sync with University Academic Dept (matching Student ID).")
    public ResponseEntity<?> syncWithUniversity(@RequestBody List<UserEntity> universityData) {
        userService.syncWithUniversity(universityData);
        return ResponseEntity.ok("Sync completed successfully!");
    }

    @GetMapping("/wishlist")
    @Operation(summary = "Get my wishlist", description = "Lists books saved by the current user.")
    public ResponseEntity<?> getWishlist(@AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(bookInteractionService.getWishlist(principal.getUser().getUserId()));
    }

    @GetMapping("/wishlist/{bookId}/status")
    @Operation(summary = "Check wishlist status")
    public ResponseEntity<?> getWishlistStatus(
            @PathVariable Long bookId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(java.util.Map.of(
                "saved", bookInteractionService.isSaved(principal.getUser().getUserId(), bookId)));
    }

    @PostMapping("/wishlist")
    @Operation(summary = "Add to Wishlist", description = "Add a specific book to the user's wishlist.")
    public ResponseEntity<?> addToWishlist(
            @Valid @RequestBody WishlistRequestDTO request,
            @AuthenticationPrincipal CustomUserDetails principal) {
        return ResponseEntity.ok(bookInteractionService.addToWishlist(
                principal.getUser().getUserId(), request.getBookId()));
    }

    @DeleteMapping("/wishlist/{bookId}")
    @Operation(summary = "Remove from Wishlist")
    public ResponseEntity<Void> removeFromWishlist(
            @PathVariable Long bookId,
            @AuthenticationPrincipal CustomUserDetails principal) {
        bookInteractionService.removeFromWishlist(principal.getUser().getUserId(), bookId);
        return ResponseEntity.noContent().build();
    }
}
