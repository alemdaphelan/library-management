package com.huit.library.modules.facilities.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/rooms")
@Tag(name = "7. Facilities & Rooms", description = "Endpoints for booking study rooms")
public class RoomController {

    @PostMapping("/book")
    @Operation(summary = "Book Room", description = "Book a study room.")
    public ResponseEntity<?> bookRoom() {
        return ResponseEntity.ok().build();
    }
}
