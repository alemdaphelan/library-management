package com.huit.library.modules.catalog.repository;

import com.huit.library.modules.catalog.entity.BookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<BookEntity, Long> {
    Optional<BookEntity> findFirstByIsbn(String isbn);
    Page<BookEntity> findByTitleContainingIgnoreCaseOrIsbnContainingIgnoreCaseOrAuthorContainingIgnoreCase(String title, String isbn, String author, Pageable pageable);
    
    Page<BookEntity> findByCategory_CategoryId(Long categoryId, Pageable pageable);
    
    Page<BookEntity> findByCategory_CategoryIdAndTitleContainingIgnoreCaseOrCategory_CategoryIdAndAuthorContainingIgnoreCase(
            Long categoryId1, String title, Long categoryId2, String author, Pageable pageable);

    @Query(value = """
            SELECT b.* FROM books b
            WHERE b.title IS NOT NULL AND BTRIM(b.title) <> ''
              AND EXISTS (
                    SELECT 1 FROM book_subject_assignments assignment
                    WHERE assignment.book_id = b.book_id
                  )
            ORDER BY COALESCE(b.publish_year, 0) DESC, LOWER(BTRIM(b.title)), b.book_id
            """, nativeQuery = true)
    List<BookEntity> findRecommendationFallback(Pageable pageable);

    @Query(value = """
            SELECT b.* FROM books b
            WHERE b.title IS NOT NULL AND BTRIM(b.title) <> ''
            ORDER BY COALESCE(b.publish_year, 0) DESC, LOWER(BTRIM(b.title)), b.book_id
            """, nativeQuery = true)
    List<BookEntity> findCatalogFallback(Pageable pageable);

    List<BookEntity> findByCategory_CategoryIdAndBookIdNotOrderByBookIdAsc(
            Long categoryId, Long bookId, Pageable pageable);

    List<BookEntity> findByBookIdNotOrderByBookIdAsc(Long bookId, Pageable pageable);

    List<BookEntity> findByCategory_CategoryIdInOrderByBookIdAsc(
            Collection<Long> categoryIds, Pageable pageable);

    @Query(value = """
            SELECT DISTINCT b.*
            FROM books b
            JOIN book_subject_assignments assignment ON assignment.book_id = b.book_id
            JOIN department_subject_affinities affinity
              ON affinity.subject_id = assignment.subject_id
             AND affinity.active = TRUE
            WHERE affinity.department_key = :departmentKey
              AND (:categoryId IS NULL OR b.category_id = :categoryId)
              AND (
                    :query = ''
                    OR LOWER(COALESCE(b.title, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.author, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.isbn, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                  )
            """,
            countQuery = """
            SELECT COUNT(DISTINCT b.book_id)
            FROM books b
            JOIN book_subject_assignments assignment ON assignment.book_id = b.book_id
            JOIN department_subject_affinities affinity
              ON affinity.subject_id = assignment.subject_id
             AND affinity.active = TRUE
            WHERE affinity.department_key = :departmentKey
              AND (:categoryId IS NULL OR b.category_id = :categoryId)
              AND (
                    :query = ''
                    OR LOWER(COALESCE(b.title, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.author, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.isbn, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                  )
            """,
            nativeQuery = true)
    Page<BookEntity> searchByDepartment(
            @Param("departmentKey") String departmentKey,
            @Param("query") String query,
            @Param("categoryId") Long categoryId,
            Pageable pageable);
}
