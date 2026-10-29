package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.dto.BookCopyResponseDTO;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.BookshelfEntity;
import com.huit.library.modules.catalog.entity.FloorEntity;
import com.huit.library.modules.catalog.entity.ShelfEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.ShelfRepository;
import com.huit.library.modules.catalog.repository.ShelfSubjectRuleRepository;
import com.huit.library.modules.recommendation.repository.BookSubjectAssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class BookCopyService {

    private static final Set<String> ALLOWED_STATUSES = Set.of(
            "AVAILABLE", "BORROWED", "BORROWING", "OVERDUE", "RESERVED", "LOST", "DAMAGED", "MAINTENANCE"
    );

    private final BookCopyRepository bookCopyRepository;
    private final ShelfRepository shelfRepository;
    private final ShelfSubjectRuleRepository shelfSubjectRuleRepository;
    private final BookSubjectAssignmentRepository bookSubjectAssignmentRepository;

    public BookCopyService(
            BookCopyRepository bookCopyRepository,
            ShelfRepository shelfRepository,
            ShelfSubjectRuleRepository shelfSubjectRuleRepository,
            BookSubjectAssignmentRepository bookSubjectAssignmentRepository) {
        this.bookCopyRepository = bookCopyRepository;
        this.shelfRepository = shelfRepository;
        this.shelfSubjectRuleRepository = shelfSubjectRuleRepository;
        this.bookSubjectAssignmentRepository = bookSubjectAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public List<BookCopyResponseDTO> getCopiesByBookId(Long bookId) {
        return bookCopyRepository.findByBookId(bookId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BookCopyResponseDTO getByBarcode(String barcode) {
        return toResponse(findCopy(barcode));
    }

    @Transactional
    public BookCopyResponseDTO updateLocation(String barcode, Long shelfId) {
        BookCopyEntity copy = bookCopyRepository.findById(barcode)
                .orElseThrow(() -> new RuntimeException("Book copy not found"));
        ShelfEntity shelf = shelfRepository.findById(shelfId)
                .orElseThrow(() -> new RuntimeException("Shelf not found"));
        validateSubjectPlacement(copy, shelf);
        copy.setShelf(shelf);
        return toResponse(bookCopyRepository.save(copy));
    }

    @Transactional
    public BookCopyResponseDTO updateStatus(String barcode, String newStatus) {
        String normalizedStatus = newStatus == null ? "" : newStatus.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("Invalid book copy status");
        }
        BookCopyEntity copy = findCopy(barcode);
        copy.setStatus(normalizedStatus);
        return toResponse(bookCopyRepository.save(copy));
    }

    private BookCopyEntity findCopy(String barcode) {
        return bookCopyRepository.findById(barcode)
                .orElseThrow(() -> new RuntimeException("Book copy not found"));
    }

    private void validateSubjectPlacement(BookCopyEntity copy, ShelfEntity shelf) {
        List<Long> allowedSubjectIds = shelfSubjectRuleRepository
                .findByShelf_ShelfIdAndActiveTrueOrderBySubject_NameAsc(shelf.getShelfId())
                .stream()
                .map(rule -> rule.getSubject().getSubjectId())
                .toList();
        if (allowedSubjectIds.isEmpty()) {
            return;
        }
        if (!bookSubjectAssignmentRepository.existsByBook_BookIdAndSubject_SubjectIdIn(
                copy.getBookId(), allowedSubjectIds)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Đầu sách không thuộc chủ đề được phép xếp tại ngăn này"
            );
        }
    }

    private BookCopyResponseDTO toResponse(BookCopyEntity copy) {
        ShelfEntity shelf = copy.getShelf();
        BookshelfEntity bookshelf = shelf == null ? null : shelf.getBookshelf();
        FloorEntity floor = bookshelf == null ? null : bookshelf.getFloor();
        String location = java.util.stream.Stream.of(
                        floor == null ? null : floor.getName(),
                        bookshelf == null ? null : bookshelf.getName(),
                        shelf == null ? null : shelf.getName())
                .filter(value -> value != null && !value.isBlank())
                .collect(java.util.stream.Collectors.joining(" / "));

        return BookCopyResponseDTO.builder()
                .barcode(copy.getBarcode())
                .bookId(copy.getBookId())
                .conditionNote(copy.getConditionNote())
                .status(copy.getStatus())
                .shelfId(shelf == null ? null : shelf.getShelfId())
                .shelfName(shelf == null ? null : shelf.getName())
                .bookshelfId(bookshelf == null ? null : bookshelf.getBookshelfId())
                .bookshelfName(bookshelf == null ? null : bookshelf.getName())
                .floorId(floor == null ? null : floor.getFloorId())
                .floorName(floor == null ? null : floor.getName())
                .location(location)
                .build();
    }
}
