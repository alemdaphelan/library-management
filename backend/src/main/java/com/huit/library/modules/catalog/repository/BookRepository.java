package com.huit.library.modules.catalog.repository;

import com.huit.library.modules.catalog.entity.BookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface BookRepository extends JpaRepository<BookEntity, Long> {
    Page<BookEntity> findByTitleContainingIgnoreCaseOrIsbnContainingIgnoreCaseOrAuthorContainingIgnoreCase(String title, String isbn, String author, Pageable pageable);
    
    Page<BookEntity> findByCategory_CategoryId(Long categoryId, Pageable pageable);
    
    Page<BookEntity> findByCategory_CategoryIdAndTitleContainingIgnoreCaseOrCategory_CategoryIdAndAuthorContainingIgnoreCase(
            Long categoryId1, String title, Long categoryId2, String author, Pageable pageable);
}
