package com.huit.library.modules.inventory.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.inventory.dto.GoodsReceiptResponseDTO;
import com.huit.library.modules.inventory.dto.ImportReceiptRequestDTO;
import com.huit.library.modules.inventory.entity.GoodsReceiptEntity;
import com.huit.library.modules.inventory.entity.SupplierEntity;
import com.huit.library.modules.inventory.repository.GoodsReceiptRepository;
import com.huit.library.modules.inventory.repository.ReceiptDetailRepository;
import com.huit.library.modules.inventory.repository.SupplierRepository;
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
        InventoryService service = new InventoryService(receiptRepository, supplierRepository,
                detailRepository, bookRepository, copyRepository);

        SupplierEntity supplier = new SupplierEntity();
        supplier.setSupplierId(7L);
        supplier.setName("NXB HUIT");
        BookEntity book = new BookEntity();
        book.setBookId(12L);
        book.setIsbn("978-1");
        when(supplierRepository.findById(7L)).thenReturn(Optional.of(supplier));
        when(bookRepository.findFirstByIsbn("978-1")).thenReturn(Optional.of(book));
        when(receiptRepository.save(any())).thenAnswer(invocation -> {
            GoodsReceiptEntity receipt = invocation.getArgument(0);
            receipt.setReceiptId(21L);
            return receipt;
        });

        ImportReceiptRequestDTO.Line line = new ImportReceiptRequestDTO.Line();
        line.setIsbn("978-1");
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
    }
}
