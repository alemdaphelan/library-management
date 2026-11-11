package com.huit.library.modules.interactive.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.entity.BookshelfEntity;
import com.huit.library.modules.catalog.entity.CategoryEntity;
import com.huit.library.modules.catalog.entity.FloorEntity;
import com.huit.library.modules.catalog.entity.ShelfEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.interactive.dto.BookReviewRequestDTO;
import com.huit.library.modules.interactive.entity.ReviewEntity;
import com.huit.library.modules.interactive.entity.WishlistEntity;
import com.huit.library.modules.interactive.repository.ReviewRepository;
import com.huit.library.modules.interactive.repository.WishlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class BookInteractionServiceTest {
    private BookRepository bookRepository;
    private BookCopyRepository copyRepository;
    private ReviewRepository reviewRepository;
    private WishlistRepository wishlistRepository;
    private UserRepository userRepository;
    private LoanDetailRepository loanDetailRepository;
    private BookInteractionService service;

    @BeforeEach
    void setUp() {
        bookRepository = mock(BookRepository.class);
        copyRepository = mock(BookCopyRepository.class);
        reviewRepository = mock(ReviewRepository.class);
        wishlistRepository = mock(WishlistRepository.class);
        userRepository = mock(UserRepository.class);
        loanDetailRepository = mock(LoanDetailRepository.class);
        service =
                new BookInteractionService(
                        bookRepository,
                        copyRepository,
                        reviewRepository,
                        wishlistRepository,
                        userRepository,
                        loanDetailRepository);
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
        when(loanDetailRepository.countBorrowedItemsByUserIdAndBookId(userId, 7L)).thenReturn(1L);
        when(reviewRepository.save(any()))
                .thenAnswer(
                        invocation -> {
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
        assertThat(summary.isReviewEligible()).isFalse();
        assertThat(summary.getReviewEligibilityMessage()).contains("đã đánh giá");
    }

    @Test
    void rejectsReviewWhenReaderHasNeverBorrowedBook() {
        UUID userId = UUID.randomUUID();
        BookEntity book = new BookEntity();
        book.setBookId(7L);
        BookReviewRequestDTO request = new BookReviewRequestDTO();
        request.setRating(4);

        when(bookRepository.findById(7L)).thenReturn(Optional.of(book));
        when(reviewRepository.findByBookIdAndUserId(7L, userId)).thenReturn(Optional.empty());
        when(loanDetailRepository.countBorrowedItemsByUserIdAndBookId(userId, 7L)).thenReturn(0L);

        assertThatThrownBy(() -> service.saveReview(7L, userId, request))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                            assertThat(exception.getReason())
                                    .contains("sau khi tài liệu đã được thủ thư giao mượn");
                        });
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectsSecondReviewWithExplicitConflict() {
        UUID userId = UUID.randomUUID();
        BookEntity book = new BookEntity();
        book.setBookId(7L);
        ReviewEntity existing = new ReviewEntity();
        existing.setBookId(7L);
        existing.setUserId(userId);
        BookReviewRequestDTO request = new BookReviewRequestDTO();
        request.setRating(3);

        when(bookRepository.findById(7L)).thenReturn(Optional.of(book));
        when(reviewRepository.findByBookIdAndUserId(7L, userId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.saveReview(7L, userId, request))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(exception.getReason()).contains("đã đánh giá");
                        });
        verify(loanDetailRepository, never()).countBorrowedItemsByUserIdAndBookId(any(), any());
        verify(reviewRepository, never()).save(any());
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
        assertThat(list.get(0).getStatus()).isEqualTo("available");
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void wishlistAddSavesNewBookAndPopulatesFullMetadataAndShelfLocation() {
        UUID userId = UUID.randomUUID();
        CategoryEntity category = new CategoryEntity();
        category.setCategoryId(2L);
        category.setName("Trí tuệ nhân tạo");

        BookEntity book = new BookEntity();
        book.setBookId(15L);
        book.setTitle("Học máy thực chiến");
        book.setAuthor("Nguyễn Văn B");
        book.setCategory(category);
        book.setIsbn("9786040000015");
        book.setPublisher("NXB ĐH Công Thương");
        book.setPublishYear(2025);
        book.setIsDigital(false);
        book.setImageUrlM("https://example.com/cover-15.jpg");

        FloorEntity floor = new FloorEntity();
        floor.setName("Tầng 2");
        BookshelfEntity bookshelf = new BookshelfEntity();
        bookshelf.setName("Dãy A");
        bookshelf.setFloor(floor);
        ShelfEntity shelf = new ShelfEntity();
        shelf.setName("Kệ 03");
        shelf.setBookshelf(bookshelf);

        BookCopyEntity copy1 = new BookCopyEntity();
        copy1.setBarcode("BC0015A");
        copy1.setBookId(15L);
        copy1.setStatus("AVAILABLE");
        copy1.setShelf(shelf);

        BookCopyEntity copy2 = new BookCopyEntity();
        copy2.setBarcode("BC0015B");
        copy2.setBookId(15L);
        copy2.setStatus("BORROWED");
        copy2.setShelf(shelf);

        when(bookRepository.findById(15L)).thenReturn(Optional.of(book));
        when(wishlistRepository.findAllByUserIdAndBookIdOrderByAddedAtDesc(userId, "15"))
                .thenReturn(List.of());
        when(wishlistRepository.findByUserIdAndBookId(userId, "15")).thenReturn(Optional.empty());
        when(wishlistRepository.save(any()))
                .thenAnswer(
                        invocation -> {
                            WishlistEntity entity = invocation.getArgument(0);
                            entity.setWishlistId(UUID.randomUUID());
                            return entity;
                        });
        when(copyRepository.findByBookId(15L)).thenReturn(List.of(copy1, copy2));
        when(bookRepository.findDepartmentNamesByBookId(15L))
                .thenReturn(List.of("Công nghệ thông tin"));

        var savedDto = service.addToWishlist(userId, 15L);

        assertThat(savedDto.getBookId()).isEqualTo(15L);
        assertThat(savedDto.getTitle()).isEqualTo("Học máy thực chiến");
        assertThat(savedDto.getAuthor()).isEqualTo("Nguyễn Văn B");
        assertThat(savedDto.getCategory()).isEqualTo("Trí tuệ nhân tạo");
        assertThat(savedDto.getDepartment()).isEqualTo("Công nghệ thông tin");
        assertThat(savedDto.getLocation()).isEqualTo("Tầng 2 / Dãy A / Kệ 03");
        assertThat(savedDto.getCover()).isEqualTo("https://example.com/cover-15.jpg");
        assertThat(savedDto.getAvailableCopies()).isEqualTo(1L);
        assertThat(savedDto.getCopies()).isEqualTo(2L);
        assertThat(savedDto.getStatus()).isEqualTo("available");
        assertThat(savedDto.getIsbn()).isEqualTo("9786040000015");
        verify(wishlistRepository, times(1)).save(any());
    }

    @Test
    void wishlistAddCleansUpLegacyDuplicatesWhenPresent() {
        UUID userId = UUID.randomUUID();
        BookEntity book = new BookEntity();
        book.setBookId(21L);
        book.setTitle("Cơ sở dữ liệu");

        WishlistEntity newest = new WishlistEntity();
        newest.setWishlistId(UUID.randomUUID());
        newest.setUserId(userId);
        newest.setBookId("21");
        newest.setAddedAt(LocalDateTime.now());

        WishlistEntity olderDuplicate = new WishlistEntity();
        olderDuplicate.setWishlistId(UUID.randomUUID());
        olderDuplicate.setUserId(userId);
        olderDuplicate.setBookId("21");
        olderDuplicate.setAddedAt(LocalDateTime.now().minusMinutes(5));

        when(bookRepository.findById(21L)).thenReturn(Optional.of(book));
        when(wishlistRepository.findAllByUserIdAndBookIdOrderByAddedAtDesc(userId, "21"))
                .thenReturn(List.of(newest, olderDuplicate));

        var result = service.addToWishlist(userId, 21L);

        assertThat(result.getWishlistId()).isEqualTo(newest.getWishlistId());
        verify(wishlistRepository).deleteAll(List.of(olderDuplicate));
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void removeFromWishlistIsIdempotentWhenBookIsAlreadyUnsaved() {
        UUID userId = UUID.randomUUID();
        when(wishlistRepository.deleteByUserIdAndBookId(userId, "99")).thenReturn(0L);

        assertThatCode(() -> service.removeFromWishlist(userId, 99L)).doesNotThrowAnyException();

        verify(wishlistRepository).deleteByUserIdAndBookId(userId, "99");
    }

    @Test
    void addToWishlistRejectsNonExistentBookWith404() {
        UUID userId = UUID.randomUUID();
        when(bookRepository.findById(4040L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addToWishlist(userId, 4040L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> {
                            assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                            assertThat(ex.getReason()).contains("Không tìm thấy đầu sách #4040");
                        });
        verify(wishlistRepository, never()).save(any());
    }

    @Test
    void getWishlistSkipsDeletedBooksAndDeduplicatesEntriesWithoutFailing() {
        UUID userId = UUID.randomUUID();
        WishlistEntity valid1 = new WishlistEntity();
        valid1.setWishlistId(UUID.randomUUID());
        valid1.setUserId(userId);
        valid1.setBookId("10");
        valid1.setAddedAt(LocalDateTime.now());

        WishlistEntity duplicate1 = new WishlistEntity();
        duplicate1.setWishlistId(UUID.randomUUID());
        duplicate1.setUserId(userId);
        duplicate1.setBookId("10");
        duplicate1.setAddedAt(LocalDateTime.now().minusHours(1));

        WishlistEntity deletedBookEntry = new WishlistEntity();
        deletedBookEntry.setWishlistId(UUID.randomUUID());
        deletedBookEntry.setUserId(userId);
        deletedBookEntry.setBookId("888");
        deletedBookEntry.setAddedAt(LocalDateTime.now().minusHours(2));

        BookEntity existingBook = new BookEntity();
        existingBook.setBookId(10L);
        existingBook.setTitle("Cấu trúc dữ liệu & Giải thuật");

        when(wishlistRepository.findByUserIdOrderByAddedAtDesc(userId))
                .thenReturn(List.of(valid1, duplicate1, deletedBookEntry));
        when(bookRepository.findAllById(List.of(10L, 888L))).thenReturn(List.of(existingBook));

        var list = service.getWishlist(userId);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getBookId()).isEqualTo(10L);
        assertThat(list.get(0).getTitle()).isEqualTo("Cấu trúc dữ liệu & Giải thuật");
    }

    @Test
    void wishlistRequiresAuthenticatedUserAndIsolatesByUserId() {
        UUID userA = UUID.fromString("00000000-0000-0000-0000-002001230219");
        UUID userB = UUID.fromString("00000000-0000-0000-0000-002001239999");

        when(wishlistRepository.existsByUserIdAndBookId(userA, "5")).thenReturn(true);
        when(wishlistRepository.existsByUserIdAndBookId(userB, "5")).thenReturn(false);

        assertThat(service.isSaved(userA, 5L)).isTrue();
        assertThat(service.isSaved(userB, 5L)).isFalse();

        assertThatThrownBy(() -> service.getWishlist(null))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
