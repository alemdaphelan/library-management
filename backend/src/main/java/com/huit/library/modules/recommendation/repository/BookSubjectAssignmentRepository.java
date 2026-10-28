package com.huit.library.modules.recommendation.repository;

import com.huit.library.modules.recommendation.entity.BookSubjectAssignmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface BookSubjectAssignmentRepository extends JpaRepository<BookSubjectAssignmentEntity, Long> {
    List<BookSubjectAssignmentEntity> findByBook_BookIdOrderByConfidenceDesc(Long bookId);

    List<BookSubjectAssignmentEntity> findByBook_BookIdIn(Collection<Long> bookIds);

    boolean existsByBook_BookIdAndSubject_SubjectIdIn(Long bookId, Collection<Long> subjectIds);

    @Query("""
            select distinct book
            from BookSubjectAssignmentEntity assignment
            join assignment.book book
            where assignment.subject.subjectId in :subjectIds
              and book.bookId <> :bookId
            order by book.bookId
            """)
    List<com.huit.library.modules.catalog.entity.BookEntity> findCandidateBooksSharingSubjects(
            @Param("subjectIds") Collection<Long> subjectIds,
            @Param("bookId") Long bookId,
            Pageable pageable);

    @Query("""
            select distinct book
            from BookSubjectAssignmentEntity assignment
            join assignment.book book
            where assignment.subject.subjectId in :subjectIds
            order by book.bookId
            """)
    List<com.huit.library.modules.catalog.entity.BookEntity> findCandidateBooksBySubjects(
            @Param("subjectIds") Collection<Long> subjectIds,
            Pageable pageable);

    @Query("""
            select assignment
            from BookSubjectAssignmentEntity assignment
            join fetch assignment.book book
            left join fetch book.category
            join fetch assignment.subject
            order by assignment.book.bookId, assignment.subject.subjectId
            """)
    List<BookSubjectAssignmentEntity> findAllForEvaluation();
}
