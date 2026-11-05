package com.huit.library.modules.catalog.repository;

import com.huit.library.modules.catalog.entity.ShelfSubjectRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShelfSubjectRuleRepository extends JpaRepository<ShelfSubjectRuleEntity, Long> {
    @org.springframework.data.jpa.repository.Query("""
            select rule from ShelfSubjectRuleEntity rule
            join fetch rule.shelf shelf join fetch shelf.bookshelf bookshelf join fetch bookshelf.floor floor
            join fetch rule.subject subject
            where rule.active=true and subject.active=true
              and exists(select a.assignmentId from BookSubjectAssignmentEntity a where a.book.bookId=:bookId and a.subject.subjectId=subject.subjectId)
            order by floor.floorId,bookshelf.bookshelfId,shelf.shelfId,subject.subjectId
            """)
    List<ShelfSubjectRuleEntity> findMatchingPlacementRules(@org.springframework.data.repository.query.Param("bookId") Long bookId);
    List<ShelfSubjectRuleEntity> findByShelf_ShelfIdAndActiveTrueOrderBySubject_NameAsc(Long shelfId);

    void deleteByShelf_ShelfId(Long shelfId);
}
