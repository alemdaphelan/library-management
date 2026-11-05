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
                copy.barcode, copy.bookId, coalesce(book.title,'Bản sao chưa đối chiếu được đầu sách'), book.isbn, copy.status, case when book.bookId is null then false else true end)
            from BookCopyEntity copy left join BookEntity book on copy.bookId=book.bookId
            where copy.shelf.shelfId = :shelfId
            order by lower(coalesce(book.title,'Bản sao chưa đối chiếu được đầu sách')), copy.barcode
            """)
    List<ShelfBookDTO> findShelfBooks(@Param("shelfId") Long shelfId);

    @Query(value = """
            select new com.huit.library.modules.catalog.dto.ShelfBookDTO(
                copy.barcode, copy.bookId, coalesce(book.title,'Bản sao chưa đối chiếu được đầu sách'), book.isbn, copy.status, case when book.bookId is null then false else true end)
            from BookCopyEntity copy left join copy.shelf shelf left join BookEntity book on copy.bookId=book.bookId
            where (:shelfId is null and shelf is null) or shelf.shelfId=:shelfId
            order by lower(coalesce(book.title,'Bản sao chưa đối chiếu được đầu sách')), copy.barcode
            """, countQuery = """
            select count(copy) from BookCopyEntity copy left join copy.shelf shelf
            where (:shelfId is null and shelf is null) or shelf.shelfId=:shelfId
            """)
    org.springframework.data.domain.Page<ShelfBookDTO> findLocationBooks(@Param("shelfId") Long shelfId,
            org.springframework.data.domain.Pageable pageable);

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
