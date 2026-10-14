package com.huit.library.modules.catalog.repository;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookCopyRepository extends JpaRepository<BookCopyEntity, String> {
    List<BookCopyEntity> findByBookId(Long bookId);
    List<BookCopyEntity> findByBookIdAndStatus(Long bookId, String status);
    Optional<BookCopyEntity> findFirstByBookIdAndStatus(Long bookId, String status);
    long countByBookIdAndStatus(Long bookId, String status);
}
