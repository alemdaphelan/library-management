package com.huit.library.modules.inventory.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.entity.ShelfEntity;
import com.huit.library.modules.catalog.entity.BookshelfEntity;
import com.huit.library.modules.catalog.entity.FloorEntity;
import com.huit.library.modules.catalog.repository.ShelfRepository;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.inventory.dto.GoodsReceiptResponseDTO;
import com.huit.library.modules.inventory.dto.ImportReceiptRequestDTO;
import com.huit.library.modules.inventory.entity.GoodsReceiptEntity;
import com.huit.library.modules.inventory.entity.SupplierEntity;
import com.huit.library.modules.inventory.repository.GoodsReceiptRepository;
import com.huit.library.modules.inventory.repository.ReceiptDetailRepository;
import com.huit.library.modules.inventory.repository.SupplierRepository;
import com.huit.library.modules.notification.service.StaffNotificationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InventoryServiceTest {
    @Test
    void importBooksPersistsDetailsAndCreatesCopies() {
        GoodsReceiptRepository receiptRepository = mock(GoodsReceiptRepository.class);
        SupplierRepository supplierRepository = mock(SupplierRepository.class);
        ReceiptDetailRepository detailRepository = mock(ReceiptDetailRepository.class);
        BookRepository bookRepository = mock(BookRepository.class);
        BookCopyRepository copyRepository = mock(BookCopyRepository.class);
        ShelfRepository shelfRepository = mock(ShelfRepository.class);
        InventoryService service = new InventoryService(receiptRepository, supplierRepository,
                detailRepository, bookRepository, copyRepository, mock(StaffNotificationService.class), shelfRepository);
        FloorEntity floor = new FloorEntity(); floor.setFloorId(1L); floor.setName("Tầng 1");
        BookshelfEntity bookshelf = new BookshelfEntity(); bookshelf.setBookshelfId(2L); bookshelf.setName("Kệ A"); bookshelf.setFloor(floor);
        ShelfEntity shelf = new ShelfEntity(); shelf.setShelfId(3L); shelf.setName("Ngăn B"); shelf.setBookshelf(bookshelf);
        when(shelfRepository.findImportLocationsForUpdate(any())).thenReturn(List.of(shelf));
        when(detailRepository.save(any())).thenAnswer(invocation -> { var detail = (com.huit.library.modules.inventory.entity.ReceiptDetailEntity) invocation.getArgument(0); detail.setDetailId(30L); return detail; });

        SupplierEntity supplier = new SupplierEntity();
        supplier.setSupplierId(7L);
        supplier.setName("NXB HUIT");
        BookEntity book = new BookEntity();
        book.setBookId(12L);
        book.setIsbn("9780132350884");
        when(supplierRepository.findById(7L)).thenReturn(Optional.of(supplier));
        when(bookRepository.findInventoryIsbnCandidates("9780132350884", "0132350882")).thenReturn(List.of(book));
        when(receiptRepository.save(any())).thenAnswer(invocation -> {
            GoodsReceiptEntity receipt = invocation.getArgument(0);
            receipt.setReceiptId(21L);
            return receipt;
        });

        ImportReceiptRequestDTO.Line line = new ImportReceiptRequestDTO.Line();
        line.setIsbn("9780132350884");
        line.setShelfId(3L);
        line.setQuantity(2);
        line.setUnitPrice(new BigDecimal("50000"));
        ImportReceiptRequestDTO request = new ImportReceiptRequestDTO();
        request.setSupplierId(7L);
        request.setLines(List.of(line));

        GoodsReceiptResponseDTO result = service.importBooks(request);

        assertThat(result.getQuantity()).isEqualTo(2);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("100000");
        verify(detailRepository).save(any());
        verify(copyRepository, times(2)).save(any());
        assertThat(result.getLines().get(0).locationPath()).isEqualTo("Tầng 1 / Kệ A / Ngăn B");
        verify(copyRepository, times(2)).save(argThat(copy -> copy.getShelf() == shelf && copy.getReceiptDetailId().equals(30L)));
    }
}
