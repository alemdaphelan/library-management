package com.huit.library.modules.interactive.repository;

import com.huit.library.modules.interactive.entity.ReviewEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReviewRepository extends JpaRepository<ReviewEntity, Long> {
    List<ReviewEntity> findByBookIdOrderByUpdatedAtDesc(Long bookId);
    Optional<ReviewEntity> findByBookIdAndUserId(Long bookId, UUID userId);
    long countByBookId(Long bookId);

    @org.springframework.data.jpa.repository.Query(
            "select coalesce(avg(review.rating), 0) from ReviewEntity review where review.bookId = :bookId")
    Double averageRatingByBookId(@org.springframework.data.repository.query.Param("bookId") Long bookId);
}
