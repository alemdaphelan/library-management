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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class InventoryAuditService {
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String UNKNOWN = "UNKNOWN";

    private final InventoryAuditRepository auditRepository;
    private final InventoryDetailRepository detailRepository;
    private final BookCopyRepository copyRepository;
    private final BookRepository bookRepository;

    public InventoryAuditService(InventoryAuditRepository auditRepository,
                                 InventoryDetailRepository detailRepository,
                                 BookCopyRepository copyRepository,
                                 BookRepository bookRepository) {
        this.auditRepository = auditRepository;
        this.detailRepository = detailRepository;
        this.copyRepository = copyRepository;
        this.bookRepository = bookRepository;
    }

    @Transactional
    public InventoryAuditResponseDTO createAudit(UserEntity conductor) {
        if (conductor == null || conductor.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không xác định được người kiểm kê");
        }

        InventoryAuditEntity audit = new InventoryAuditEntity();
        audit.setConductedBy(conductor.getUserId());
        audit.setTotalSystemBooks(Math.toIntExact(copyRepository.count()));
        audit.setTotalActualBooks(0);
        audit.setStatus(IN_PROGRESS);
        return toAuditResponse(auditRepository.save(audit), List.of());
    }

    @Transactional(readOnly = true)
    public List<InventoryAuditResponseDTO> getAudits() {
        return auditRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(audit -> toAuditResponse(audit, List.of()))
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryAuditResponseDTO getAudit(Long auditId) {
        InventoryAuditEntity audit = findAudit(auditId);
        List<InventoryScanResponseDTO> details = getDetails(auditId);
        return toAuditResponse(audit, details);
    }

    @Transactional
    public InventoryScanResponseDTO scan(Long auditId, InventoryScanRequestDTO request) {
        InventoryAuditEntity audit = findAudit(auditId);
        ensureInProgress(audit);

        String barcode = request.getBarcode().trim();
        String storedAuditId = auditId.toString();
        if (detailRepository.findByAuditIdAndBarcode(storedAuditId, barcode).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Barcode đã được quét trong phiên kiểm kê này");
        }

        Optional<BookCopyEntity> copy = copyRepository.findById(barcode);
        String scannedLocation = normalizeNullable(request.getScannedLocation());
        String expectedLocation = copy.map(this::locationOf).orElse(null);

        InventoryDetailEntity detail = new InventoryDetailEntity();
        detail.setAuditId(storedAuditId);
        detail.setBarcode(barcode);
        detail.setScannedLocation(scannedLocation);
        detail.setScanStatus(resolveStatus(copy.isPresent(), scannedLocation, expectedLocation));
        detail = detailRepository.save(detail);

        if (copy.isPresent()) {
            audit.setTotalActualBooks(audit.getTotalActualBooks() + 1);
            auditRepository.save(audit);
        }

        return toScanResponse(detail, copy.orElse(null), expectedLocation);
    }

    @Transactional
    public InventoryAuditResponseDTO complete(Long auditId) {
        InventoryAuditEntity audit = findAudit(auditId);
        ensureInProgress(audit);

        List<InventoryDetailEntity> details = detailRepository.findByAuditIdOrderByCreatedAtAsc(auditId.toString());
        audit.setTotalActualBooks((int) details.stream()
                .filter(detail -> !UNKNOWN.equals(detail.getScanStatus()))
                .count());
        audit.setStatus(COMPLETED);
        audit = auditRepository.save(audit);

        return toAuditResponse(audit, details.stream().map(this::toScanResponse).toList());
    }

    private InventoryAuditEntity findAudit(Long auditId) {
        return auditRepository.findById(auditId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phiên kiểm kê"));
    }

    private void ensureInProgress(InventoryAuditEntity audit) {
        if (!IN_PROGRESS.equals(audit.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phiên kiểm kê đã hoàn tất");
        }
    }

    private List<InventoryScanResponseDTO> getDetails(Long auditId) {
        return detailRepository.findByAuditIdOrderByCreatedAtAsc(auditId.toString()).stream()
                .map(this::toScanResponse)
                .toList();
    }

    private InventoryScanResponseDTO toScanResponse(InventoryDetailEntity detail) {
        BookCopyEntity copy = copyRepository.findById(detail.getBarcode()).orElse(null);
        return toScanResponse(detail, copy, copy == null ? null : locationOf(copy));
    }

    private InventoryScanResponseDTO toScanResponse(InventoryDetailEntity detail,
                                                     BookCopyEntity copy,
                                                     String expectedLocation) {
        BookEntity book = copy == null ? null : bookRepository.findById(copy.getBookId()).orElse(null);
        return InventoryScanResponseDTO.builder()
                .detailId(detail.getDetailId())
                .auditId(Long.valueOf(detail.getAuditId()))
                .barcode(detail.getBarcode())
                .scanStatus(detail.getScanStatus())
                .expectedLocation(expectedLocation)
                .scannedLocation(detail.getScannedLocation())
                .bookId(copy == null ? null : copy.getBookId())
                .bookTitle(book == null ? null : book.getTitle())
                .copyStatus(copy == null ? null : copy.getStatus())
                .scannedAt(detail.getCreatedAt())
                .build();
    }

    private InventoryAuditResponseDTO toAuditResponse(InventoryAuditEntity audit,
                                                       List<InventoryScanResponseDTO> details) {
        return InventoryAuditResponseDTO.builder()
                .auditId(audit.getAuditId())
                .conductedBy(audit.getConductedBy())
                .totalSystemBooks(audit.getTotalSystemBooks())
                .totalActualBooks(audit.getTotalActualBooks())
                .status(audit.getStatus())
                .createdAt(audit.getCreatedAt())
                .updatedAt(audit.getUpdatedAt())
                .details(details)
                .build();
    }

    private String resolveStatus(boolean exists, String scannedLocation, String expectedLocation) {
        if (!exists) return UNKNOWN;
        if (scannedLocation == null || expectedLocation == null) return "FOUND";
        if (normalize(expectedLocation).equals(normalize(scannedLocation))) {
            return "MATCHED";
        }
        return "MISPLACED";
    }

    private String locationOf(BookCopyEntity copy) {
        ShelfEntity shelf = copy.getShelf();
        if (shelf == null) return null;
        BookshelfEntity bookshelf = shelf.getBookshelf();
        FloorEntity floor = bookshelf == null ? null : bookshelf.getFloor();
        return String.join(" / ",
                floor == null ? "Chưa rõ tầng" : floor.getName(),
                bookshelf == null ? "Chưa rõ dãy" : bookshelf.getName(),
                shelf.getName());
    }

    private String normalizeNullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
