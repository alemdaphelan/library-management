package com.huit.library.modules.catalog.repository;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import com.huit.library.modules.catalog.dto.ShelfBookDTO;

@Repository
public interface BookCopyRepository extends JpaRepository<BookCopyEntity, String> {
    List<BookCopyEntity> findByReceiptDetailIdIn(java.util.Collection<Long> receiptDetailIds);
    List<BookCopyEntity> findByBookId(Long bookId);
    List<BookCopyEntity> findByBookIdAndStatus(Long bookId, String status);
    Optional<BookCopyEntity> findFirstByBookIdAndStatus(Long bookId, String status);
    long countByBookIdAndStatus(Long bookId, String status);
    long countByStatus(String status);

    @Query("""
            select new com.huit.library.modules.catalog.dto.ShelfBookDTO(
                copy.barcode, book.bookId, book.title, book.isbn, copy.status)
            from BookCopyEntity copy, BookEntity book
            where copy.bookId = book.bookId and copy.shelf.shelfId = :shelfId
            order by lower(book.title), copy.barcode
            """)
    List<ShelfBookDTO> findShelfBooks(@Param("shelfId") Long shelfId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select copy from BookCopyEntity copy
            where copy.barcode in :barcodes
            order by copy.barcode
            """)
    List<BookCopyEntity> findAllByBarcodeInForUpdate(@Param("barcodes") Collection<String> barcodes);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BookCopyEntity> findFirstByBookIdAndStatusOrderByBarcodeAsc(Long bookId, String status);
}
