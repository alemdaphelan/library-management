package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.dto.BookResponseDTO;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.catalog.repository.CategoryRepository;
import com.huit.library.modules.catalog.dto.DepartmentOptionDTO;
import com.huit.library.modules.recommendation.repository.DepartmentSubjectAffinityRepository;
import com.huit.library.modules.recommendation.service.DepartmentKeyNormalizer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final BookCopyRepository bookCopyRepository;
    private final CategoryRepository categoryRepository;
    private final DepartmentSubjectAffinityRepository departmentRepository;

    public BookService(BookRepository bookRepository, BookCopyRepository bookCopyRepository,
                       CategoryRepository categoryRepository,
                       DepartmentSubjectAffinityRepository departmentRepository) {
        this.bookRepository = bookRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.categoryRepository = categoryRepository;
        this.departmentRepository = departmentRepository;
    }

    @Transactional(readOnly = true)
    public Page<BookResponseDTO> searchBooks(
            String query, Long categoryId, String department, Pageable pageable) {
        boolean hasQuery = query != null && !query.isBlank();
        boolean hasCategory = categoryId != null;
        Page<BookEntity> books;
        String departmentKey = DepartmentKeyNormalizer.normalize(department);
        if (!departmentKey.isBlank()) {
            books = bookRepository.searchByDepartment(
                    departmentKey, hasQuery ? query.trim() : "", categoryId, pageable);
        } else if (hasQuery && hasCategory) {
            books = bookRepository.findByCategory_CategoryIdAndTitleContainingIgnoreCaseOrCategory_CategoryIdAndAuthorContainingIgnoreCase(
                    categoryId, query, categoryId, query, pageable);
        } else if (hasCategory) {
            books = bookRepository.findByCategory_CategoryId(categoryId, pageable);
        } else if (hasQuery) {
            books = bookRepository.findByTitleContainingIgnoreCaseOrIsbnContainingIgnoreCaseOrAuthorContainingIgnoreCase(query, query, query, pageable);
        } else {
            books = bookRepository.findAll(pageable);
        }
        return books.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<DepartmentOptionDTO> getSearchDepartments() {
        return departmentRepository.findDistinctActiveDepartments();
    }

    private BookEntity getBookEntity(Long id) {
        return bookRepository.findById(id).orElseThrow(() -> new RuntimeException("Book not found"));
    }

    @Transactional(readOnly = true)
    public BookResponseDTO getBookById(Long id) {
        return toResponse(getBookEntity(id));
    }

    @Transactional
    public BookResponseDTO createBook(BookEntity book) {
        resolveCategory(book);
        return toResponse(bookRepository.save(book));
    }

    @Transactional
    public BookResponseDTO updateBook(Long id, BookEntity updatedBook) {
        BookEntity existing = getBookEntity(id);
        resolveCategory(updatedBook);
        existing.setTitle(updatedBook.getTitle());
        existing.setIsbn(updatedBook.getIsbn());
        existing.setDdcCallNumber(updatedBook.getDdcCallNumber());
        existing.setPublishYear(updatedBook.getPublishYear());
        existing.setLanguage(updatedBook.getLanguage());
        existing.setLabelColor(updatedBook.getLabelColor());
        existing.setDefaultPrice(updatedBook.getDefaultPrice());
        existing.setIsDigital(updatedBook.getIsDigital());
        existing.setAuthor(updatedBook.getAuthor());
        existing.setPublisher(updatedBook.getPublisher());
        existing.setCategory(updatedBook.getCategory());
        existing.setImageUrlS(updatedBook.getImageUrlS());
        existing.setImageUrlM(updatedBook.getImageUrlM());
        existing.setImageUrlL(updatedBook.getImageUrlL());
        return toResponse(bookRepository.save(existing));
    }

    public void deleteBook(Long id) {
        bookRepository.deleteById(id);
    }

    private void resolveCategory(BookEntity book) {
        if (book.getCategory() != null && book.getCategory().getCategoryId() != null) {
            book.setCategory(categoryRepository.findById(book.getCategory().getCategoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found")));
        } else {
            book.setCategory(null);
        }
    }

    private BookResponseDTO toResponse(BookEntity book) {
        List<BookCopyEntity> copies = bookCopyRepository.findByBookId(book.getBookId());
        long availableCopies = copies.stream()
                .filter(copy -> "AVAILABLE".equalsIgnoreCase(copy.getStatus()))
                .count();
        String location = copies.stream()
                .map(BookCopyEntity::getShelf)
                .filter(java.util.Objects::nonNull)
                .map(shelf -> {
                    var bookshelf = shelf.getBookshelf();
                    var floor = bookshelf == null ? null : bookshelf.getFloor();
                    return java.util.stream.Stream.of(
                                    floor == null ? null : floor.getName(),
                                    bookshelf == null ? null : bookshelf.getName(),
                                    shelf.getName())
                            .filter(value -> value != null && !value.isBlank())
                            .collect(java.util.stream.Collectors.joining(" / "));
                })
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.joining(", "));

        return BookResponseDTO.builder()
                .bookId(book.getBookId())
                .title(book.getTitle())
                .isbn(book.getIsbn())
                .ddcCallNumber(book.getDdcCallNumber())
                .publishYear(book.getPublishYear())
                .language(book.getLanguage())
                .labelColor(book.getLabelColor())
                .defaultPrice(book.getDefaultPrice())
                .isDigital(book.getIsDigital())
                .author(book.getAuthor())
                .publisher(book.getPublisher())
                .category(book.getCategory())
                .imageUrlS(book.getImageUrlS())
                .imageUrlM(book.getImageUrlM())
                .imageUrlL(book.getImageUrlL())
                .copies(copies.size())
                .availableCopies(availableCopies)
                .location(location)
                .status(availableCopies > 0 ? "available" : "out_of_stock")
                .build();
    }
}
