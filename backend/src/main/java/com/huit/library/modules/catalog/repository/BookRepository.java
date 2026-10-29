package com.huit.library.modules.catalog.repository;

import com.huit.library.modules.catalog.entity.BookEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Collection;
import java.util.Optional;

@Repository
public interface BookRepository extends JpaRepository<BookEntity, Long> {
    @Query(value = """
            select * from books where regexp_replace(upper(coalesce(isbn, '')), '[[:space:]-]', '', 'g') = :isbn13
               or (:isbn10 <> '' and regexp_replace(upper(coalesce(isbn, '')), '[[:space:]-]', '', 'g') = :isbn10)
            order by book_id
            """, nativeQuery = true)
    List<BookEntity> findInventoryIsbnCandidates(@Param("isbn13") String isbn13, @Param("isbn10") String isbn10);
    Optional<BookEntity> findFirstByIsbn(String isbn);
    Page<BookEntity> findByTitleContainingIgnoreCaseOrIsbnContainingIgnoreCaseOrAuthorContainingIgnoreCase(String title, String isbn, String author, Pageable pageable);
    
    Page<BookEntity> findByCategory_CategoryId(Long categoryId, Pageable pageable);
    
    Page<BookEntity> findByCategory_CategoryIdAndTitleContainingIgnoreCaseOrCategory_CategoryIdAndAuthorContainingIgnoreCase(
            Long categoryId1, String title, Long categoryId2, String author, Pageable pageable);

    @Modifying
    @Query("update BookEntity book set book.viewCount = coalesce(book.viewCount, 0) + 1 where book.bookId = :bookId")
    int incrementViewCount(@Param("bookId") Long bookId);

    @Query(value = """
            SELECT DISTINCT b.*
            FROM books b
            LEFT JOIN categories c ON c.category_id = b.category_id
            WHERE (:categoryId IS NULL OR b.category_id = :categoryId)
              AND (
                    :availability = ''
                    OR (:availability = 'AVAILABLE' AND EXISTS (
                        SELECT 1 FROM book_copies available_copy
                        WHERE available_copy.book_id = b.book_id
                          AND UPPER(COALESCE(available_copy.status, '')) = 'AVAILABLE'
                    ))
                    OR (:availability = 'UNAVAILABLE' AND NOT EXISTS (
                        SELECT 1 FROM book_copies available_copy
                        WHERE available_copy.book_id = b.book_id
                          AND UPPER(COALESCE(available_copy.status, '')) = 'AVAILABLE'
                    ))
                  )
              AND (
                    :query = ''
                    OR LOWER(COALESCE(b.title, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.author, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.isbn, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.publisher, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(c.name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR EXISTS (
                        SELECT 1
                        FROM book_subject_assignments assignment
                        JOIN recommendation_subjects subject
                          ON subject.subject_id = assignment.subject_id
                        WHERE assignment.book_id = b.book_id
                          AND (
                                LOWER(subject.name) LIKE LOWER(CONCAT('%', :query, '%'))
                                OR LOWER(COALESCE(subject.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                                OR LOWER(COALESCE(assignment.evidence, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                                OR (:subjectCode <> '' AND subject.code = :subjectCode)
                              )
                    )
                  )
            """,
            countQuery = """
            SELECT COUNT(DISTINCT b.book_id)
            FROM books b
            LEFT JOIN categories c ON c.category_id = b.category_id
            WHERE (:categoryId IS NULL OR b.category_id = :categoryId)
              AND (
                    :availability = ''
                    OR (:availability = 'AVAILABLE' AND EXISTS (
                        SELECT 1 FROM book_copies available_copy
                        WHERE available_copy.book_id = b.book_id
                          AND UPPER(COALESCE(available_copy.status, '')) = 'AVAILABLE'
                    ))
                    OR (:availability = 'UNAVAILABLE' AND NOT EXISTS (
                        SELECT 1 FROM book_copies available_copy
                        WHERE available_copy.book_id = b.book_id
                          AND UPPER(COALESCE(available_copy.status, '')) = 'AVAILABLE'
                    ))
                  )
              AND (
                    :query = ''
                    OR LOWER(COALESCE(b.title, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.author, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.isbn, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.publisher, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(c.name, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR EXISTS (
                        SELECT 1
                        FROM book_subject_assignments assignment
                        JOIN recommendation_subjects subject
                          ON subject.subject_id = assignment.subject_id
                        WHERE assignment.book_id = b.book_id
                          AND (
                                LOWER(subject.name) LIKE LOWER(CONCAT('%', :query, '%'))
                                OR LOWER(COALESCE(subject.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                                OR LOWER(COALESCE(assignment.evidence, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                                OR (:subjectCode <> '' AND subject.code = :subjectCode)
                              )
                    )
                  )
            """,
            nativeQuery = true)
    Page<BookEntity> searchCatalog(
            @Param("query") String query,
            @Param("subjectCode") String subjectCode,
            @Param("categoryId") Long categoryId,
            @Param("availability") String availability,
            Pageable pageable);

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
            JOIN recommendation_subjects assigned_subject
              ON assigned_subject.subject_id = assignment.subject_id
            JOIN department_subject_affinities affinity
              ON affinity.subject_id = assignment.subject_id
             AND affinity.active = TRUE
            WHERE affinity.department_key = :departmentKey
              AND (:categoryId IS NULL OR b.category_id = :categoryId)
              AND (
                    :availability = ''
                    OR (:availability = 'AVAILABLE' AND EXISTS (
                        SELECT 1 FROM book_copies available_copy
                        WHERE available_copy.book_id = b.book_id
                          AND UPPER(COALESCE(available_copy.status, '')) = 'AVAILABLE'
                    ))
                    OR (:availability = 'UNAVAILABLE' AND NOT EXISTS (
                        SELECT 1 FROM book_copies available_copy
                        WHERE available_copy.book_id = b.book_id
                          AND UPPER(COALESCE(available_copy.status, '')) = 'AVAILABLE'
                    ))
                  )
              AND (
                    :query = ''
                    OR LOWER(COALESCE(b.title, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.author, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.isbn, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.publisher, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(assigned_subject.name) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(assigned_subject.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR (:subjectCode <> '' AND assigned_subject.code = :subjectCode)
                  )
            """,
            countQuery = """
            SELECT COUNT(DISTINCT b.book_id)
            FROM books b
            JOIN book_subject_assignments assignment ON assignment.book_id = b.book_id
            JOIN recommendation_subjects assigned_subject
              ON assigned_subject.subject_id = assignment.subject_id
            JOIN department_subject_affinities affinity
              ON affinity.subject_id = assignment.subject_id
             AND affinity.active = TRUE
            WHERE affinity.department_key = :departmentKey
              AND (:categoryId IS NULL OR b.category_id = :categoryId)
              AND (
                    :availability = ''
                    OR (:availability = 'AVAILABLE' AND EXISTS (
                        SELECT 1 FROM book_copies available_copy
                        WHERE available_copy.book_id = b.book_id
                          AND UPPER(COALESCE(available_copy.status, '')) = 'AVAILABLE'
                    ))
                    OR (:availability = 'UNAVAILABLE' AND NOT EXISTS (
                        SELECT 1 FROM book_copies available_copy
                        WHERE available_copy.book_id = b.book_id
                          AND UPPER(COALESCE(available_copy.status, '')) = 'AVAILABLE'
                    ))
                  )
              AND (
                    :query = ''
                    OR LOWER(COALESCE(b.title, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.author, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.isbn, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.publisher, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(b.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(assigned_subject.name) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR LOWER(COALESCE(assigned_subject.description, '')) LIKE LOWER(CONCAT('%', :query, '%'))
                    OR (:subjectCode <> '' AND assigned_subject.code = :subjectCode)
                  )
            """,
            nativeQuery = true)
    Page<BookEntity> searchByDepartment(
            @Param("departmentKey") String departmentKey,
            @Param("query") String query,
            @Param("subjectCode") String subjectCode,
            @Param("categoryId") Long categoryId,
            @Param("availability") String availability,
            Pageable pageable);
}
