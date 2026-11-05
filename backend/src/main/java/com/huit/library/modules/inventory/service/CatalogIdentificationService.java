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
    private final com.huit.library.modules.catalog.service.ShelfPlacementService placement;
    public CatalogIdentificationService(InventoryService inventory, BookSubjectAssignmentRepository subjects,com.huit.library.modules.catalog.service.ShelfPlacementService placement) {
        this.inventory = inventory; this.subjects = subjects;this.placement=placement;
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
        var candidates=placement.candidates(book.getBookId());
        return new CatalogIdentificationDTO(book.getBookId(), book.getTitle(), book.getIsbn(), canonical, book.getAuthor(), book.getPublisher(),
                book.getPublishYear(), book.getDefaultPrice(), category == null ? null : category.getCategoryId(), category == null ? null : category.getName(),
                book.getDdcCallNumber(), classification, category == null && classification.isEmpty() ? "MISSING" : "AVAILABLE",
                candidates.isEmpty()?"NO_MATCHING_RULE":"SUGGESTED_BY_ADMIN_RULE",
                candidates.isEmpty()?null:candidates.get(0).shelfId(),
                candidates.isEmpty()?"Chưa có ngăn khớp chủ đề theo quy định Admin. Chọn ngăn không hạn chế hoặc bổ sung phân loại/quy tắc."
                    :"Đã chọn ngăn phù hợp quy định chủ đề của Admin. Có thể chọn vị trí hợp lệ khác; server kiểm tra lại lúc lưu.",candidates);
    }
}
