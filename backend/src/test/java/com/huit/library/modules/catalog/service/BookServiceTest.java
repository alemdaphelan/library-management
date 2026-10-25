package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.dto.BookResponseDTO;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.entity.CategoryEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.catalog.repository.CategoryRepository;
import com.huit.library.modules.recommendation.repository.DepartmentSubjectAffinityRepository;
import com.huit.library.modules.interactive.repository.ReviewRepository;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BookServiceTest {
    @Test
    void updateBookUsesBookIdAndResolvesManagedCategory() {
        BookRepository bookRepository = mock(BookRepository.class);
        BookCopyRepository copyRepository = mock(BookCopyRepository.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        DepartmentSubjectAffinityRepository departmentRepository =
                mock(DepartmentSubjectAffinityRepository.class);
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        LoanDetailRepository loanDetailRepository = mock(LoanDetailRepository.class);
        BookService service = new BookService(
                bookRepository, copyRepository, categoryRepository, departmentRepository,
                reviewRepository, loanDetailRepository);

        BookEntity existing = new BookEntity();
        existing.setBookId(42L);
        existing.setTitle("Old title");
        CategoryEntity requestedCategory = new CategoryEntity();
        requestedCategory.setCategoryId(3L);
        CategoryEntity managedCategory = new CategoryEntity();
        managedCategory.setCategoryId(3L);
        managedCategory.setName("Công nghệ thông tin");
        BookEntity request = new BookEntity();
        request.setTitle("New title");
        request.setAuthor("HUIT");
        request.setCategory(requestedCategory);

        when(bookRepository.findById(42L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(managedCategory));
        when(bookRepository.save(existing)).thenReturn(existing);
        when(copyRepository.findByBookId(42L)).thenReturn(List.of());

        BookResponseDTO result = service.updateBook(42L, request);

        assertThat(result.getBookId()).isEqualTo(42L);
        assertThat(result.getTitle()).isEqualTo("New title");
        assertThat(result.getCategory().getName()).isEqualTo("Công nghệ thông tin");
        verify(bookRepository).save(existing);
    }

    @Test
    void searchByDepartmentUsesNormalizedTaxonomyKey() {
        BookRepository bookRepository = mock(BookRepository.class);
        BookCopyRepository copyRepository = mock(BookCopyRepository.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        DepartmentSubjectAffinityRepository departmentRepository =
                mock(DepartmentSubjectAffinityRepository.class);
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        LoanDetailRepository loanDetailRepository = mock(LoanDetailRepository.class);
        BookService service = new BookService(
                bookRepository, copyRepository, categoryRepository, departmentRepository,
                reviewRepository, loanDetailRepository);
        BookEntity book = new BookEntity();
        book.setBookId(7L);
        book.setTitle("Nhập môn lập trình");
        var pageable = PageRequest.of(0, 10);
        when(bookRepository.searchByDepartment(
                "khoa cong nghe thong tin", "java", "", null, "", pageable))
                .thenReturn(new PageImpl<>(List.of(book), pageable, 1));
        when(copyRepository.findByBookId(7L)).thenReturn(List.of());

        var result = service.searchBooks(
                "java", null, "Khoa Công nghệ Thông tin", null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(bookRepository).searchByDepartment(
                "khoa cong nghe thong tin", "java", "", null, "", pageable);
    }

    @Test
    void contentSearchMapsKnnToTheAiSubjectTaxonomy() {
        BookRepository bookRepository = mock(BookRepository.class);
        BookCopyRepository copyRepository = mock(BookCopyRepository.class);
        CategoryRepository categoryRepository = mock(CategoryRepository.class);
        DepartmentSubjectAffinityRepository departmentRepository =
                mock(DepartmentSubjectAffinityRepository.class);
        ReviewRepository reviewRepository = mock(ReviewRepository.class);
        LoanDetailRepository loanDetailRepository = mock(LoanDetailRepository.class);
        BookService service = new BookService(
                bookRepository, copyRepository, categoryRepository, departmentRepository,
                reviewRepository, loanDetailRepository);
        BookEntity book = new BookEntity();
        book.setBookId(8L);
        book.setTitle("Trí tuệ nhân tạo");
        var pageable = PageRequest.of(0, 10);
        when(bookRepository.searchCatalog("K-NN", "IT_DATA_AI", null, "", pageable))
                .thenReturn(new PageImpl<>(List.of(book), pageable, 1));
        when(copyRepository.findByBookId(8L)).thenReturn(List.of());

        var result = service.searchBooks("K-NN", null, null, null, pageable);

        assertThat(result.getContent()).extracting(BookResponseDTO::getTitle)
                .containsExactly("Trí tuệ nhân tạo");
        verify(bookRepository).searchCatalog("K-NN", "IT_DATA_AI", null, "", pageable);
    }

    @Test
    void contentSearchMapsVietnameseAiAbbreviationToTheAiSubjectTaxonomy() {
        BookRepository bookRepository = mock(BookRepository.class);
        BookCopyRepository copyRepository = mock(BookCopyRepository.class);
        BookService service = new BookService(
                bookRepository, copyRepository, mock(CategoryRepository.class),
                mock(DepartmentSubjectAffinityRepository.class), mock(ReviewRepository.class),
                mock(LoanDetailRepository.class));
        var pageable = PageRequest.of(0, 10);
        when(bookRepository.searchCatalog("TTNT", "IT_DATA_AI", null, "", pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        service.searchBooks("TTNT", null, null, null, pageable);

        verify(bookRepository).searchCatalog("TTNT", "IT_DATA_AI", null, "", pageable);
    }

    @Test
    void availableFilterIsAppliedBeforePagination() {
        BookRepository bookRepository = mock(BookRepository.class);
        BookCopyRepository copyRepository = mock(BookCopyRepository.class);
        BookService service = new BookService(
                bookRepository, copyRepository, mock(CategoryRepository.class),
                mock(DepartmentSubjectAffinityRepository.class), mock(ReviewRepository.class),
                mock(LoanDetailRepository.class));
        BookEntity book = new BookEntity();
        book.setBookId(9L);
        book.setTitle("Sách còn bản");
        var pageable = PageRequest.of(0, 12);
        when(bookRepository.searchCatalog("", "", null, "AVAILABLE", pageable))
                .thenReturn(new PageImpl<>(List.of(book), pageable, 1));
        when(copyRepository.findByBookId(9L)).thenReturn(List.of());

        var result = service.searchBooks(null, null, null, "available", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(bookRepository).searchCatalog("", "", null, "AVAILABLE", pageable);
    }

    @Test
    void exactIsbnLookupReturnsCatalogMetadataWithoutIncrementingViews() {
        BookRepository bookRepository = mock(BookRepository.class);
        BookCopyRepository copyRepository = mock(BookCopyRepository.class);
        BookService service = new BookService(
                bookRepository, copyRepository, mock(CategoryRepository.class),
                mock(DepartmentSubjectAffinityRepository.class), mock(ReviewRepository.class),
                mock(LoanDetailRepository.class));
        BookEntity book = new BookEntity();
        book.setBookId(15L);
        book.setIsbn("9780132350884");
        book.setTitle("Clean Code");
        when(bookRepository.findFirstByIsbn("9780132350884")).thenReturn(Optional.of(book));
        when(copyRepository.findByBookId(15L)).thenReturn(List.of());

        BookResponseDTO result = service.getBookByIsbn(" 9780132350884 ");

        assertThat(result.getTitle()).isEqualTo("Clean Code");
        verify(bookRepository, never()).incrementViewCount(any());
    }
}
