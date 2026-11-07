package com.huit.library.modules.catalog.repository;

import com.huit.library.modules.catalog.entity.ShelfSubjectRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ShelfSubjectRuleRepository extends JpaRepository<ShelfSubjectRuleEntity, Long> {
    // Catalog-only groups organize books without assigning a subject for recommendation or reporting.
    String BOOK_MATCHES_SUBJECT = """
            (book.bookId is not null and (
                (subject.code <> 'SHELF_UNCLASSIFIED'
                  and exists(select a.assignmentId from BookSubjectAssignmentEntity a
                             where a.book=book and a.subject=subject))
                or ((subject.code = 'SHELF_UNCLASSIFIED'
                     or subject.code = function('shelf_author_group', book.author, book.title))
                  and not exists(select a.assignmentId from BookSubjectAssignmentEntity a
                                 where a.book=book and a.subject.active=true))
            ))
            """;

    @Query("""
            select rule from ShelfSubjectRuleEntity rule
            join fetch rule.shelf shelf join fetch shelf.bookshelf bookshelf join fetch bookshelf.floor floor
            join fetch rule.subject subject
            where rule.active=true and subject.active=true
              and exists(select book.bookId from BookEntity book where book.bookId=:bookId and
            """ + BOOK_MATCHES_SUBJECT + """
              )
            order by floor.floorId,bookshelf.bookshelfId,shelf.shelfId,subject.subjectId
            """)
    List<ShelfSubjectRuleEntity> findMatchingPlacementRules(@Param("bookId") Long bookId);

    @Query("""
            select count(rule) > 0 from ShelfSubjectRuleEntity rule
            where rule.shelf.shelfId=:shelfId and rule.active=true and rule.subject.active=true
            """)
    boolean hasActiveRules(@Param("shelfId") Long shelfId);

    @Query("""
            select count(rule) > 0 from ShelfSubjectRuleEntity rule join rule.subject subject
            where rule.shelf.shelfId=:shelfId and rule.active=true and subject.active=true
              and exists(select book.bookId from BookEntity book where book.bookId=:bookId and
            """ + BOOK_MATCHES_SUBJECT + ")")
    boolean allowsPlacement(@Param("shelfId") Long shelfId, @Param("bookId") Long bookId);

    List<ShelfSubjectRuleEntity> findByShelf_ShelfIdAndActiveTrueOrderBySubject_NameAsc(Long shelfId);

    void deleteByShelf_ShelfId(Long shelfId);
}
