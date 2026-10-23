package com.huit.library.modules.interactive.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.interactive.dto.BookReviewRequestDTO;
import com.huit.library.modules.interactive.entity.ReviewEntity;
import com.huit.library.modules.interactive.entity.WishlistEntity;
import com.huit.library.modules.interactive.repository.ReviewRepository;
import com.huit.library.modules.interactive.repository.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BookInteractionServiceTest {
    private BookRepository bookRepository;
    private BookCopyRepository copyRepository;
    private ReviewRepository reviewRepository;
    private WishlistRepository wishlistRepository;
    private UserRepository userRepository;
    private BookInteractionService service;

    @BeforeEach
    void setUp() {
        bookRepository = mock(BookRepository.class);
        copyRepository = mock(BookCopyRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        userRepository = mock(UserRepository.class);
        service = new BookInteractionService(
                bookRepository, copyRepository, reviewRepository, wishlistRepository, userRepository);
    }

    @Test
    void savesOneReviewPerReaderAndReturnsRealSummary() {
        UUID userId = UUID.randomUUID();
        BookEntity book = new BookEntity();
        book.setBookId(7L);
        book.setTitle("Machine Learning");
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setFullName("Nguyễn Văn A");
        AtomicReference<ReviewEntity> saved = new AtomicReference<>();
        BookReviewRequestDTO request = new BookReviewRequestDTO();
        request.setRating(5);
        request.setComment("  Nội dung hữu ích  ");

        when(bookRepository.findById(7L)).thenReturn(Optional.of(book));
        when(reviewRepository.findByBookIdAndUserId(7L, userId)).thenReturn(Optional.empty());
        when(reviewRepository.save(any())).thenAnswer(invocation -> {
            ReviewEntity review = invocation.getArgument(0);
            review.setReviewId(11L);
            saved.set(review);
            return review;
        });
        when(reviewRepository.findByBookIdOrderByUpdatedAtDesc(7L))
                .thenAnswer(invocation -> List.of(saved.get()));
        when(reviewRepository.averageRatingByBookId(7L)).thenReturn(5.0);
        when(userRepository.findAllById(any())).thenReturn(List.of(user));

        var summary = service.saveReview(7L, userId, request);

        assertThat(summary.getAverageRating()).isEqualTo(5.0);
        assertThat(summary.getReviewCount()).isEqualTo(1);
        assertThat(summary.getMyReview().getComment()).isEqualTo("Nội dung hữu ích");
        assertThat(summary.getMyReview().isMine()).isTrue();
    }

    @Test
    void wishlistAddIsIdempotentAndListsRealAvailability() {
        UUID userId = UUID.randomUUID();
        BookEntity book = new BookEntity();
        book.setBookId(9L);
        book.setTitle("K-NN căn bản");
        book.setAuthor("HUIT");
        WishlistEntity entry = new WishlistEntity();
        entry.setWishlistId(UUID.randomUUID());
        entry.setUserId(userId);
        entry.setBookId("9");
        entry.setAddedAt(LocalDateTime.now());

        when(bookRepository.findById(9L)).thenReturn(Optional.of(book));
        when(wishlistRepository.findByUserIdAndBookId(userId, "9")).thenReturn(Optional.of(entry));
        when(wishlistRepository.findByUserIdOrderByAddedAtDesc(userId)).thenReturn(List.of(entry));
        when(bookRepository.findAllById(List.of(9L))).thenReturn(List.of(book));
        when(copyRepository.countByBookIdAndStatus(9L, "AVAILABLE")).thenReturn(2L);

        var added = service.addToWishlist(userId, 9L);
        var list = service.getWishlist(userId);

        assertThat(added.getBookId()).isEqualTo(9L);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).getAvailableCopies()).isEqualTo(2L);
        verify(wishlistRepository, never()).save(any());
    }
}
