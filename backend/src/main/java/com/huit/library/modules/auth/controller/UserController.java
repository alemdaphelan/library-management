package com.huit.library.modules.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@Tag(name = "2. Users", description = "Endpoints for user management and synchronization")
public class UserController {

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
