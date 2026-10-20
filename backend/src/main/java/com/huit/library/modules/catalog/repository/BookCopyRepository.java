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

@Repository
public interface BookCopyRepository extends JpaRepository<BookCopyEntity, String> {
    List<BookCopyEntity> findByBookId(Long bookId);
    List<BookCopyEntity> findByBookIdAndStatus(Long bookId, String status);
    Optional<BookCopyEntity> findFirstByBookIdAndStatus(Long bookId, String status);
    long countByBookIdAndStatus(Long bookId, String status);
    long countByStatus(String status);

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
