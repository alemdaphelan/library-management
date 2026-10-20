package com.huit.library.modules.catalog.controller;

import com.huit.library.modules.catalog.dto.LocationRequestDTO;
import com.huit.library.modules.catalog.service.LocationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/locations")
@PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
public class LocationController {
    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/floors")
    public ResponseEntity<?> getFloors() { return ResponseEntity.ok(locationService.getFloors()); }

    @PostMapping("/floors")
    public ResponseEntity<?> createFloor(@Valid @RequestBody LocationRequestDTO request) {
        return ResponseEntity.ok(locationService.createFloor(request));
    }

    @PutMapping("/floors/{id}")
    public ResponseEntity<?> updateFloor(@PathVariable Long id, @Valid @RequestBody LocationRequestDTO request) {
        return ResponseEntity.ok(locationService.updateFloor(id, request));
    }

    @DeleteMapping("/floors/{id}")
    public ResponseEntity<?> deleteFloor(@PathVariable Long id) {
        locationService.deleteFloor(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/bookshelves")
    public ResponseEntity<?> getBookshelves(@RequestParam Long floorId) {
        return ResponseEntity.ok(locationService.getBookshelves(floorId));
    }

    @PostMapping("/bookshelves")
    public ResponseEntity<?> createBookshelf(@Valid @RequestBody LocationRequestDTO request) {
        return ResponseEntity.ok(locationService.createBookshelf(request));
    }

    @PutMapping("/bookshelves/{id}")
    public ResponseEntity<?> updateBookshelf(@PathVariable Long id, @Valid @RequestBody LocationRequestDTO request) {
        return ResponseEntity.ok(locationService.updateBookshelf(id, request));
    }

    @DeleteMapping("/bookshelves/{id}")
    public ResponseEntity<?> deleteBookshelf(@PathVariable Long id) {
        locationService.deleteBookshelf(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shelves")
    public ResponseEntity<?> getShelves(@RequestParam Long bookshelfId) {
        return ResponseEntity.ok(locationService.getShelves(bookshelfId));
    }

    @PostMapping("/shelves")
    public ResponseEntity<?> createShelf(@Valid @RequestBody LocationRequestDTO request) {
        return ResponseEntity.ok(locationService.createShelf(request));
    }

    @PutMapping("/shelves/{id}")
    public ResponseEntity<?> updateShelf(@PathVariable Long id, @Valid @RequestBody LocationRequestDTO request) {
        return ResponseEntity.ok(locationService.updateShelf(id, request));
    }

    @DeleteMapping("/shelves/{id}")
    public ResponseEntity<?> deleteShelf(@PathVariable Long id) {
        locationService.deleteShelf(id);
        return ResponseEntity.noContent().build();
    }
}
