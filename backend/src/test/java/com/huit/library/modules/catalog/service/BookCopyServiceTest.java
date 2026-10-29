package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.dto.BookCopyResponseDTO;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.BookshelfEntity;
import com.huit.library.modules.catalog.entity.FloorEntity;
import com.huit.library.modules.catalog.entity.ShelfEntity;
import com.huit.library.modules.catalog.entity.ShelfSubjectRuleEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.ShelfRepository;
import com.huit.library.modules.catalog.repository.ShelfSubjectRuleRepository;
import com.huit.library.modules.recommendation.entity.RecommendationSubjectEntity;
import com.huit.library.modules.recommendation.repository.BookSubjectAssignmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BookCopyServiceTest {
    private BookCopyRepository repository;
    private ShelfRepository shelfRepository;
    private ShelfSubjectRuleRepository shelfSubjectRuleRepository;
    private BookSubjectAssignmentRepository assignmentRepository;
    private BookCopyService service;

    @BeforeEach
    void setUp() {
        repository = mock(BookCopyRepository.class);
        shelfRepository = mock(ShelfRepository.class);
        shelfSubjectRuleRepository = mock(ShelfSubjectRuleRepository.class);
        assignmentRepository = mock(BookSubjectAssignmentRepository.class);
        service = new BookCopyService(
                repository, shelfRepository, shelfSubjectRuleRepository, assignmentRepository);
    }

    @Test
    void getByBarcodeReturnsFullLocationHierarchy() {
        FloorEntity floor = new FloorEntity();
        floor.setFloorId(1L);
        floor.setName("Tầng 2");
        BookshelfEntity bookshelf = new BookshelfEntity();
        bookshelf.setBookshelfId(2L);
        bookshelf.setName("Kệ CNTT");
        bookshelf.setFloor(floor);
        ShelfEntity shelf = new ShelfEntity();
        shelf.setShelfId(3L);
        shelf.setName("Ngăn A");
        shelf.setBookshelf(bookshelf);
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode("BC-001");
        copy.setBookId(10L);
        copy.setStatus("AVAILABLE");
        copy.setShelf(shelf);
        when(repository.findById("BC-001")).thenReturn(Optional.of(copy));

        BookCopyResponseDTO result = service.getByBarcode("BC-001");

        assertThat(result.getLocation()).isEqualTo("Tầng 2 / Kệ CNTT / Ngăn A");
        assertThat(result.getBookId()).isEqualTo(10L);
    }

    @Test
    void updateStatusRejectsUnknownState() {
        assertThatThrownBy(() -> service.updateStatus("BC-001", "UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateLocationRejectsABookOutsideTheShelfSubjectRules() {
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode("BC-001");
        copy.setBookId(10L);
        ShelfEntity shelf = new ShelfEntity();
        shelf.setShelfId(3L);
        RecommendationSubjectEntity subject = new RecommendationSubjectEntity();
        subject.setSubjectId(8L);
        subject.setCode("IT_DATA_AI");
        subject.setName("CNTT - Dữ liệu - AI");
        ShelfSubjectRuleEntity rule = new ShelfSubjectRuleEntity();
        rule.setShelf(shelf);
        rule.setSubject(subject);

        when(repository.findById("BC-001")).thenReturn(Optional.of(copy));
        when(shelfRepository.findById(3L)).thenReturn(Optional.of(shelf));
        when(shelfSubjectRuleRepository
                .findByShelf_ShelfIdAndActiveTrueOrderBySubject_NameAsc(3L))
                .thenReturn(List.of(rule));
        when(assignmentRepository.existsByBook_BookIdAndSubject_SubjectIdIn(10L, List.of(8L)))
                .thenReturn(false);

        assertThatThrownBy(() -> service.updateLocation("BC-001", 3L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("không thuộc chủ đề");
        verify(repository, never()).save(any());
    }

    @Test
    void updateLocationAcceptsABookMatchingAnAllowedSubject() {
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode("BC-001");
        copy.setBookId(10L);
        copy.setStatus("AVAILABLE");
        ShelfEntity shelf = new ShelfEntity();
        shelf.setShelfId(3L);
        shelf.setName("Ngăn AI");
        RecommendationSubjectEntity subject = new RecommendationSubjectEntity();
        subject.setSubjectId(8L);
        ShelfSubjectRuleEntity rule = new ShelfSubjectRuleEntity();
        rule.setShelf(shelf);
        rule.setSubject(subject);

        when(repository.findById("BC-001")).thenReturn(Optional.of(copy));
        when(shelfRepository.findById(3L)).thenReturn(Optional.of(shelf));
        when(shelfSubjectRuleRepository
                .findByShelf_ShelfIdAndActiveTrueOrderBySubject_NameAsc(3L))
                .thenReturn(List.of(rule));
        when(assignmentRepository.existsByBook_BookIdAndSubject_SubjectIdIn(10L, List.of(8L)))
                .thenReturn(true);
        when(repository.save(copy)).thenReturn(copy);

        BookCopyResponseDTO result = service.updateLocation("BC-001", 3L);

        assertThat(result.getShelfId()).isEqualTo(3L);
        verify(repository).save(copy);
    }
}
