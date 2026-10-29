package com.huit.library.modules.inventory.controller;

import com.huit.library.modules.inventory.dto.CatalogIdentificationDTO;
import com.huit.library.modules.inventory.service.CatalogIdentificationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/inventory/catalog-identification")
@PreAuthorize("hasAnyRole('ADMIN', 'LIBRARIAN')")
public class InventoryCatalogController {
    private final CatalogIdentificationService service;
    public InventoryCatalogController(CatalogIdentificationService service) { this.service = service; }
    @GetMapping
    public CatalogIdentificationDTO identify(@RequestParam(required = false) String isbn, @RequestParam(required = false) Long bookId) {
        return service.identify(isbn, bookId);
    }
}
