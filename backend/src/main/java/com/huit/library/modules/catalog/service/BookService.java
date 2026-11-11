package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.dto.BookResponseDTO;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.entity.CategoryEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.catalog.repository.CategoryRepository;
import com.huit.library.modules.catalog.dto.DepartmentOptionDTO;
import com.huit.library.modules.recommendation.repository.DepartmentSubjectAffinityRepository;
import com.huit.library.modules.recommendation.service.DepartmentKeyNormalizer;
import com.huit.library.modules.interactive.repository.ReviewRepository;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.time.LocalDate;

@Service
public class BookService {

    private final BookRepository bookRepository;
    private final BookCopyRepository bookCopyRepository;
    private final CategoryRepository categoryRepository;
    private final DepartmentSubjectAffinityRepository departmentRepository;
    private final ReviewRepository reviewRepository;
    private final LoanDetailRepository loanDetailRepository;

    public BookService(
            BookRepository bookRepository,
            BookCopyRepository bookCopyRepository,
            CategoryRepository categoryRepository,
            DepartmentSubjectAffinityRepository departmentRepository,
            ReviewRepository reviewRepository,
            LoanDetailRepository loanDetailRepository) {
        this.bookRepository = bookRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.categoryRepository = categoryRepository;
        this.departmentRepository = departmentRepository;
        this.reviewRepository = reviewRepository;
        this.loanDetailRepository = loanDetailRepository;
    }

    @Transactional(readOnly = true)
    public Page<BookResponseDTO> searchBooks(
            String query,
            Long categoryId,
            String department,
            String availability,
            Pageable pageable) {
        return searchBooks(query, categoryId, department, availability, null, pageable);
    }

    @Transactional(readOnly = true)
    public Page<BookResponseDTO> searchBooks(
            String query,
            Long categoryId,
            String department,
            String availability,
            String facultyId,
            Pageable pageable) {
        boolean hasQuery = query != null && !query.isBlank();
        Page<BookEntity> books;
        String departmentKey = DepartmentKeyNormalizer.normalize(department);
        String availabilityFilter = normalizeAvailability(availability);
        if (!departmentKey.isBlank()) {
            books =
                    bookRepository.searchByDepartment(
                            departmentKey,
                            hasQuery ? query.trim() : "",
                            resolveSubjectCode(hasQuery ? query.trim() : ""),
                            categoryId,
                            availabilityFilter,
                            facultyId == null ? "" : facultyId.trim(),
                            pageable);
        } else {
            String normalizedQuery = hasQuery ? query.trim() : "";
            books =
                    bookRepository.searchCatalog(
                            normalizedQuery,
                            resolveSubjectCode(normalizedQuery),
                            categoryId,
                            availabilityFilter,
                            facultyId == null ? "" : facultyId.trim(),
                            pageable);
        }
        return books.map(this::toResponse);
    }

    private String normalizeAvailability(String availability) {
        if (availability == null
                || availability.isBlank()
                || "ALL".equalsIgnoreCase(availability)) {
            return "";
        }
        if ("AVAILABLE".equalsIgnoreCase(availability)) {
            return "AVAILABLE";
        }
        if ("UNAVAILABLE".equalsIgnoreCase(availability)
                || "OUT_OF_STOCK".equalsIgnoreCase(availability)
                || "BORROWED".equalsIgnoreCase(availability)) {
            return "UNAVAILABLE";
        }
        throw new IllegalArgumentException("Trạng thái khả dụng không hợp lệ: " + availability);
    }

    @Transactional(readOnly = true)
    public List<DepartmentOptionDTO> getSearchDepartments() {
        return departmentRepository.findDistinctActiveDepartments();
    }

    private BookEntity getBookEntity(Long id) {
        return bookRepository
                .findById(id)
                .orElseThrow(
                        () ->
                                new org.springframework.web.server.ResponseStatusException(
                                        org.springframework.http.HttpStatus.NOT_FOUND,
                                        "Không tìm thấy tài liệu yêu cầu."));
    }

    @Transactional
    public BookResponseDTO getBookById(Long id) {
        if (bookRepository.incrementViewCount(id) == 0) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Không tìm thấy tài liệu yêu cầu.");
        }
        return toResponse(getBookEntity(id));
    }

    @Transactional(readOnly = true)
    public BookResponseDTO getBookByIsbn(String isbn) {
        String normalized = isbn == null ? "" : isbn.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("ISBN không được để trống");
        }
        BookEntity book =
                bookRepository
                        .findFirstByIsbn(normalized)
                        .orElseThrow(
                                () ->
                                        new org.springframework.web.server.ResponseStatusException(
                                                org.springframework.http.HttpStatus.NOT_FOUND,
                                                "Không tìm thấy đầu sách có ISBN " + normalized));
        return toResponse(book);
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
        existing.setPriceCurrency(updatedBook.getPriceCurrency());
        existing.setPriceSource(updatedBook.getPriceSource());
        existing.setPriceUpdatedAt(updatedBook.getPriceUpdatedAt());
        existing.setPriceStatus(updatedBook.getPriceStatus());
        existing.setMaterialType(updatedBook.getMaterialType());
        existing.setIsDigital(updatedBook.getIsDigital());
        existing.setAuthor(updatedBook.getAuthor());
        existing.setPublisher(updatedBook.getPublisher());
        existing.setDescription(updatedBook.getDescription());
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
            book.setCategory(
                    categoryRepository
                            .findById(book.getCategory().getCategoryId())
                            .orElseThrow(() -> new IllegalArgumentException("Category not found")));
        } else {
            book.setCategory(null);
        }
    }

    private BookResponseDTO toResponse(BookEntity book) {
        List<BookCopyEntity> copies = bookCopyRepository.findByBookId(book.getBookId());
        long availableCopies =
                copies.stream()
                        .filter(copy -> "AVAILABLE".equalsIgnoreCase(copy.getStatus()))
                        .count();
        String location =
                copies.stream()
                        .map(BookCopyEntity::getShelf)
                        .filter(java.util.Objects::nonNull)
                        .map(
                                shelf -> {
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
                .priceCurrency(book.getPriceCurrency())
                .priceSource(book.getPriceSource())
                .priceUpdatedAt(book.getPriceUpdatedAt())
                .priceStatus(book.getPriceStatus())
                .materialType(book.getMaterialType())
                .isDigital(book.getIsDigital())
                .author(book.getAuthor())
                .publisher(book.getPublisher())
                .description(resolveDescription(book))
                .category(book.getCategory())
                .department(resolveDepartment(book))
                .imageUrlS(book.getImageUrlS())
                .imageUrlM(book.getImageUrlM())
                .imageUrlL(book.getImageUrlL())
                .copies(copies.size())
                .availableCopies(availableCopies)
                .location(location)
                .status(availableCopies > 0 ? "available" : "out_of_stock")
                .rating(roundRating(reviewRepository.averageRatingByBookId(book.getBookId())))
                .reviewsCount(reviewRepository.countByBookId(book.getBookId()))
                .borrowCount(
                        loanDetailRepository.countBorrowedByBookIdSince(
                                book.getBookId(), LocalDate.now().withDayOfYear(1).atStartOfDay()))
                .viewCount(book.getViewCount() == null ? 0L : book.getViewCount())
                .build();
    }

    private String resolveSubjectCode(String query) {
        String normalized = DepartmentKeyNormalizer.normalize(query);
        if (normalized.isBlank()) {
            return "";
        }
        if (containsAny(
                normalized,
                "k nn",
                "knn",
                "nearest neighbor",
                "machine learning",
                "hoc may",
                "deep learning",
                "tri tue nhan tao",
                "ttnt",
                "artificial intelligence",
                "ai")) {
            return "IT_DATA_AI";
        }
        if (containsAny(normalized, "an toan thuc pham", "food technology", "food science")) {
            return "FOOD_TECH";
        }
        if (containsAny(normalized, "logistics", "supply chain", "chuoi cung ung")) {
            return "LOGISTICS_INDUSTRIAL";
        }
        return "";
    }

    private boolean containsAny(String value, String... terms) {
        for (String term : terms) {
            if ("ai".equals(term)) {
                if (java.util.Arrays.asList(value.split(" ")).contains("ai")) {
                    return true;
                }
            } else if (value.contains(term)) {
                return true;
            }
        }
        return false;
    }

    private String resolveDepartment(BookEntity book) {
        if (book.getCategory() != null
                && CategoryEntity.GENERAL_EDUCATION.equals(book.getCategory().getName())) {
            return "Giáo dục đại cương · Dùng chung";
        }
        if (book.getBookId() != null) {
            List<String> names = bookRepository.findOfficialMajorNamesByBookId(book.getBookId());
            if (names == null || names.isEmpty()) {
                names = bookRepository.findDepartmentNamesByBookId(book.getBookId());
            }
            if (names != null && !names.isEmpty()) {
                String joined =
                        names.stream()
                                .filter(value -> value != null && !value.isBlank())
                                .distinct()
                                .limit(2)
                                .collect(java.util.stream.Collectors.joining(", "));
                if (!joined.isBlank()) {
                    return joined;
                }
            }
        }
        if (book.getCategory() != null
                && book.getCategory().getName() != null
                && !book.getCategory().getName().isBlank()) {
            return book.getCategory().getName();
        }
        return "Chưa gán ngành";
    }

    private String resolveDescription(BookEntity book) {
        if (book.getDescription() != null && !book.getDescription().isBlank()) {
            return book.getDescription();
        }
        String category =
                book.getCategory() == null || book.getCategory().getName() == null
                        ? "Tài liệu chung"
                        : book.getCategory().getName();
        return "Tài liệu "
                + book.getTitle()
                + " thuộc chủ đề "
                + category
                + ", phục vụ học tập, nghiên cứu và tham khảo tại Thư viện HUIT.";
    }

    private double roundRating(Double rating) {
        if (rating == null) {
            return 0.0;
        }
        return Math.round(rating * 10.0) / 10.0;
    }
}
