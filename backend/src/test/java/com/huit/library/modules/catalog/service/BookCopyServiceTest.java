package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.dto.BookCopyResponseDTO;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.BookshelfEntity;
import com.huit.library.modules.catalog.entity.FloorEntity;
import com.huit.library.modules.catalog.entity.ShelfEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.ShelfRepository;
import com.huit.library.modules.catalog.repository.ShelfSubjectRuleRepository;
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
    private BookCopyService service;

    @BeforeEach
    void setUp() {
        repository = mock(BookCopyRepository.class);
        shelfRepository = mock(ShelfRepository.class);
        shelfSubjectRuleRepository = mock(ShelfSubjectRuleRepository.class);
        service = new BookCopyService(
                repository, shelfRepository, new ShelfPlacementService(shelfSubjectRuleRepository));
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
        when(repository.findAllByBarcodeInForUpdate(List.of("BC-001"))).thenReturn(List.of(copy));
        when(shelfRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(shelf));
        when(shelfSubjectRuleRepository.hasActiveRules(3L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateLocation("BC-001", 3L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("không thuộc chủ đề");
        verify(repository, never()).save(any());
    }

    @Test
    void updateLocationRejectsCopyWhenShelfIsFull() {
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode("BC-001");
        copy.setBookId(10L);
        ShelfEntity shelf = new ShelfEntity();
        shelf.setShelfId(3L);
        shelf.setCapacity(1);
        when(repository.findAllByBarcodeInForUpdate(List.of("BC-001"))).thenReturn(List.of(copy));
        when(shelfRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(shelf));
        when(shelfSubjectRuleRepository.allowsPlacement(3L, 10L)).thenReturn(true);
        when(repository.countByShelf_ShelfId(3L)).thenReturn(1L);

        assertThatThrownBy(() -> service.updateLocation("BC-001", 3L))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("đủ sức chứa");
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
        when(repository.findAllByBarcodeInForUpdate(List.of("BC-001"))).thenReturn(List.of(copy));
        when(shelfRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(shelf));
        when(shelfSubjectRuleRepository.allowsPlacement(3L, 10L)).thenReturn(true);
        when(repository.save(copy)).thenReturn(copy);

        BookCopyResponseDTO result = service.updateLocation("BC-001", 3L);

        assertThat(result.getShelfId()).isEqualTo(3L);
        verify(repository).save(copy);
    }
}
