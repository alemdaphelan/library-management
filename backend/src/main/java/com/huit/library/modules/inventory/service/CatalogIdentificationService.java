package com.huit.library.modules.inventory.service;

import com.huit.library.modules.inventory.dto.CatalogIdentificationDTO;
import com.huit.library.modules.inventory.dto.ImportReceiptRequestDTO;
import com.huit.library.modules.recommendation.repository.BookSubjectAssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogIdentificationService {
    private final InventoryService inventory;
    private final BookSubjectAssignmentRepository subjects;
    public CatalogIdentificationService(InventoryService inventory, BookSubjectAssignmentRepository subjects) {
        this.inventory = inventory; this.subjects = subjects;
    }
    @Transactional(readOnly = true)
    public CatalogIdentificationDTO identify(String isbn, Long bookId) {
        boolean hasIsbn = isbn != null && !isbn.isBlank();
        if (hasIsbn == (bookId != null) || (bookId != null && bookId <= 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chọn đúng một định danh ISBN hoặc bookId hợp lệ");
        }
        var line = new ImportReceiptRequestDTO.Line(); line.setBookId(bookId); line.setIsbn(isbn);
        var book = inventory.resolveBook(line);
        String canonical = null;
        try { canonical = Isbn.canonical(book.getIsbn()); } catch (ResponseStatusException ignored) { /* Legacy catalog can still be addressed by bookId. */ }
        var classification = subjects.findByBook_BookIdOrderByConfidenceDesc(book.getBookId()).stream()
                .filter(assignment -> Boolean.TRUE.equals(assignment.getSubject().getActive()))
                .map(assignment -> new CatalogIdentificationDTO.Subject(assignment.getSubject().getCode(), assignment.getSubject().getName(),
                        assignment.getConfidence(), assignment.getClassificationSource(), assignment.getEvidence(), Boolean.TRUE.equals(assignment.getPrimarySubject())))
                .toList();
        var category = book.getCategory();
        return new CatalogIdentificationDTO(book.getBookId(), book.getTitle(), book.getIsbn(), canonical, book.getAuthor(), book.getPublisher(),
                book.getPublishYear(), book.getDefaultPrice(), category == null ? null : category.getCategoryId(), category == null ? null : category.getName(),
                book.getDdcCallNumber(), classification, category == null && classification.isEmpty() ? "MISSING" : "AVAILABLE",
                "WAITING_ADMIN_RULES", null, "Chưa có quy tắc xếp sách được quản lý. Chọn ngăn thủ công; chưa có đề xuất tự động.");
    }
}
