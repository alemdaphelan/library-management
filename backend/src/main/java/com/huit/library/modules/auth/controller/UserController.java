package com.huit.library.modules.auth.controller;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@Tag(name = "2. Users", description = "Endpoints for user management and synchronization")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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

    @PostMapping("/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Create a new student manually")
    public ResponseEntity<?> createStudent(@RequestBody UserEntity student) {
        return ResponseEntity.ok(userService.createStudent(student));
    }

    @PutMapping("/students/{mssv}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Update an existing student")
    public ResponseEntity<?> updateStudent(@PathVariable String mssv, @RequestBody UserEntity student) {
        return ResponseEntity.ok(userService.updateStudent(mssv, student));
    }

    @DeleteMapping("/students/{mssv}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a student")
    public ResponseEntity<?> deleteStudent(@PathVariable String mssv) {
        userService.deleteStudent(mssv);
        return ResponseEntity.ok("Student deleted successfully");
    }


    @PostMapping("/sync")
    @Operation(summary = "Sync University Data", description = "Trigger sync with University Academic Dept (matching Student ID).")
    public ResponseEntity<?> syncWithUniversity(@RequestBody List<UserEntity> universityData) {
        userService.syncWithUniversity(universityData);
        return ResponseEntity.ok("Sync completed successfully!");
    }

    @PostMapping("/wishlist")
    @Operation(summary = "Add to Wishlist", description = "Add a specific book to the user's wishlist.")
    public ResponseEntity<?> addToWishlist() {
        // Will be connected to Interactive module WishlistService later
        return ResponseEntity.ok("Added to wishlist successfully!");
    }
}
