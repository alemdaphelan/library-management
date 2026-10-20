package com.huit.library.modules.inventory.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.entity.BookshelfEntity;
import com.huit.library.modules.catalog.entity.FloorEntity;
import com.huit.library.modules.catalog.entity.ShelfEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.inventory.dto.InventoryAuditResponseDTO;
import com.huit.library.modules.inventory.dto.InventoryScanRequestDTO;
import com.huit.library.modules.inventory.dto.InventoryScanResponseDTO;
import com.huit.library.modules.inventory.entity.InventoryAuditEntity;
import com.huit.library.modules.inventory.entity.InventoryDetailEntity;
import com.huit.library.modules.inventory.repository.InventoryAuditRepository;
import com.huit.library.modules.inventory.repository.InventoryDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryAuditServiceTest {
    private InventoryAuditRepository auditRepository;
    private InventoryDetailRepository detailRepository;
    private BookCopyRepository copyRepository;
    private BookRepository bookRepository;
    private InventoryAuditService service;

    @BeforeEach
    void setUp() {
        auditRepository = mock(InventoryAuditRepository.class);
        detailRepository = mock(InventoryDetailRepository.class);
        copyRepository = mock(BookCopyRepository.class);
        bookRepository = mock(BookRepository.class);
        service = new InventoryAuditService(auditRepository, detailRepository, copyRepository, bookRepository);

        when(auditRepository.save(any())).thenAnswer(invocation -> {
            InventoryAuditEntity audit = invocation.getArgument(0);
            if (audit.getAuditId() == null) audit.setAuditId(9L);
            return audit;
        });
        when(detailRepository.save(any())).thenAnswer(invocation -> {
            InventoryDetailEntity detail = invocation.getArgument(0);
            detail.setDetailId("detail-1");
            return detail;
        });
    }

    @Test
    void createAuditCapturesSystemCopyCountAndConductor() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        when(copyRepository.count()).thenReturn(125L);

        InventoryAuditResponseDTO result = service.createAudit(user);

        assertThat(result.getAuditId()).isEqualTo(9L);
        assertThat(result.getConductedBy()).isEqualTo(userId);
        assertThat(result.getTotalSystemBooks()).isEqualTo(125);
        assertThat(result.getTotalActualBooks()).isZero();
        assertThat(result.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void scanMatchesBarcodeAndExpectedLocation() {
        InventoryAuditEntity audit = inProgressAudit();
        BookCopyEntity copy = locatedCopy();
        BookEntity book = new BookEntity();
        book.setBookId(2L);
        book.setTitle("Clean Code");
        when(auditRepository.findById(9L)).thenReturn(Optional.of(audit));
        when(detailRepository.findByAuditIdAndBarcode("9", "BC-001")).thenReturn(Optional.empty());
        when(copyRepository.findById("BC-001")).thenReturn(Optional.of(copy));
        when(bookRepository.findById(2L)).thenReturn(Optional.of(book));

        InventoryScanRequestDTO request = new InventoryScanRequestDTO();
        request.setBarcode(" BC-001 ");
        request.setScannedLocation("Tầng 1 / Dãy A / Ngăn 01");
        InventoryScanResponseDTO result = service.scan(9L, request);

        assertThat(result.getScanStatus()).isEqualTo("MATCHED");
        assertThat(result.getExpectedLocation()).isEqualTo("Tầng 1 / Dãy A / Ngăn 01");
        assertThat(result.getBookTitle()).isEqualTo("Clean Code");
        assertThat(audit.getTotalActualBooks()).isEqualTo(1);
    }

    @Test
    void scanRecordsUnknownBarcodeWithoutIncreasingActualCount() {
        InventoryAuditEntity audit = inProgressAudit();
        when(auditRepository.findById(9L)).thenReturn(Optional.of(audit));
        when(detailRepository.findByAuditIdAndBarcode("9", "MISSING")).thenReturn(Optional.empty());
        when(copyRepository.findById("MISSING")).thenReturn(Optional.empty());

        InventoryScanRequestDTO request = new InventoryScanRequestDTO();
        request.setBarcode("MISSING");
        InventoryScanResponseDTO result = service.scan(9L, request);

        assertThat(result.getScanStatus()).isEqualTo("UNKNOWN");
        assertThat(audit.getTotalActualBooks()).isZero();
        verify(auditRepository, never()).save(audit);
    }

    @Test
    void scanFlagsCopyFoundAtDifferentLocation() {
        InventoryAuditEntity audit = inProgressAudit();
        BookCopyEntity copy = locatedCopy();
        when(auditRepository.findById(9L)).thenReturn(Optional.of(audit));
        when(detailRepository.findByAuditIdAndBarcode("9", "BC-001")).thenReturn(Optional.empty());
        when(copyRepository.findById("BC-001")).thenReturn(Optional.of(copy));
        when(bookRepository.findById(2L)).thenReturn(Optional.empty());

        InventoryScanRequestDTO request = new InventoryScanRequestDTO();
        request.setBarcode("BC-001");
        request.setScannedLocation("Tầng 2 / Dãy B / Ngăn 09");

        assertThat(service.scan(9L, request).getScanStatus()).isEqualTo("MISPLACED");
    }

    @Test
    void scanRejectsDuplicateBarcode() {
        InventoryAuditEntity audit = inProgressAudit();
        when(auditRepository.findById(9L)).thenReturn(Optional.of(audit));
        when(detailRepository.findByAuditIdAndBarcode("9", "BC-001"))
                .thenReturn(Optional.of(new InventoryDetailEntity()));

        InventoryScanRequestDTO request = new InventoryScanRequestDTO();
        request.setBarcode("BC-001");

        assertThatThrownBy(() -> service.scan(9L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("đã được quét");
        verify(detailRepository, never()).save(any());
    }

    @Test
    void completeRecalculatesActualCountAndClosesAudit() {
        InventoryAuditEntity audit = inProgressAudit();
        InventoryDetailEntity found = detail("FOUND", "BC-001");
        InventoryDetailEntity misplaced = detail("MISPLACED", "BC-002");
        InventoryDetailEntity unknown = detail("UNKNOWN", "MISSING");
        when(auditRepository.findById(9L)).thenReturn(Optional.of(audit));
        when(detailRepository.findByAuditIdOrderByCreatedAtAsc("9"))
                .thenReturn(List.of(found, misplaced, unknown));
        when(copyRepository.findById(anyString())).thenReturn(Optional.empty());

        InventoryAuditResponseDTO result = service.complete(9L);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getTotalActualBooks()).isEqualTo(2);
        assertThat(result.getDetails()).hasSize(3);
    }

    @Test
    void completedAuditCannotReceiveMoreScans() {
        InventoryAuditEntity audit = inProgressAudit();
        audit.setStatus("COMPLETED");
        when(auditRepository.findById(9L)).thenReturn(Optional.of(audit));
        InventoryScanRequestDTO request = new InventoryScanRequestDTO();
        request.setBarcode("BC-001");

        assertThatThrownBy(() -> service.scan(9L, request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("đã hoàn tất");
        verify(detailRepository, never()).save(any());
    }

    private InventoryAuditEntity inProgressAudit() {
        InventoryAuditEntity audit = new InventoryAuditEntity();
        audit.setAuditId(9L);
        audit.setTotalSystemBooks(10);
        audit.setTotalActualBooks(0);
        audit.setStatus("IN_PROGRESS");
        return audit;
    }

    private BookCopyEntity locatedCopy() {
        FloorEntity floor = new FloorEntity();
        floor.setName("Tầng 1");
        BookshelfEntity bookshelf = new BookshelfEntity();
        bookshelf.setName("Dãy A");
        bookshelf.setFloor(floor);
        ShelfEntity shelf = new ShelfEntity();
        shelf.setName("Ngăn 01");
        shelf.setBookshelf(bookshelf);
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode("BC-001");
        copy.setBookId(2L);
        copy.setStatus("AVAILABLE");
        copy.setShelf(shelf);
        return copy;
    }

    private InventoryDetailEntity detail(String status, String barcode) {
        InventoryDetailEntity detail = new InventoryDetailEntity();
        detail.setDetailId(UUID.randomUUID().toString());
        detail.setAuditId("9");
        detail.setBarcode(barcode);
        detail.setScanStatus(status);
        return detail;
    }
}
