package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.dto.BookCopyResponseDTO;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.BookshelfEntity;
import com.huit.library.modules.catalog.entity.FloorEntity;
import com.huit.library.modules.catalog.entity.ShelfEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.ShelfRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BookCopyServiceTest {
    private BookCopyRepository repository;
    private BookCopyService service;

    @BeforeEach
    void setUp() {
        repository = mock(BookCopyRepository.class);
        service = new BookCopyService(repository, mock(ShelfRepository.class));
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
}
