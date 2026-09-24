package com.huit.library.modules.auth.controller;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/students/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Get student by ID")
    public ResponseEntity<?> getStudentById(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getStudentById(id));
    }

    @PostMapping("/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Create a new student manually")
    public ResponseEntity<?> createStudent(@RequestBody UserEntity student) {
        return ResponseEntity.ok(userService.createStudent(student));
    }

    @PutMapping("/students/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
    @Operation(summary = "Update an existing student")
    public ResponseEntity<?> updateStudent(@PathVariable UUID id, @RequestBody UserEntity student) {
        return ResponseEntity.ok(userService.updateStudent(id, student));
    }

    @DeleteMapping("/students/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete a student")
    public ResponseEntity<?> deleteStudent(@PathVariable UUID id) {
        userService.deleteStudent(id);
        return ResponseEntity.ok("Student deleted successfully");
    }

    @PostMapping("/register")
    @Operation(summary = "Register User", description = "Register a new user in the system.")
    public ResponseEntity<?> register() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/sync")
    @Operation(summary = "Sync University Data", description = "Trigger sync with University Academic Dept (matching Student ID).")
    public ResponseEntity<?> syncWithUniversity() {
        return ResponseEntity.ok().build();
    }

    @PostMapping("/wishlist")
    @Operation(summary = "Add to Wishlist", description = "Add a specific book to the user's wishlist.")
    public ResponseEntity<?> addToWishlist() {
        return ResponseEntity.ok().build();
    }
}
