package com.huit.library.modules.interactive.service;

import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.interactive.dto.BookReviewDTO;
import com.huit.library.modules.interactive.dto.BookReviewRequestDTO;
import com.huit.library.modules.interactive.dto.BookReviewSummaryDTO;
import com.huit.library.modules.interactive.dto.WishlistItemDTO;
import com.huit.library.modules.interactive.entity.ReviewEntity;
import com.huit.library.modules.interactive.entity.WishlistEntity;
import com.huit.library.modules.interactive.repository.ReviewRepository;
import com.huit.library.modules.interactive.repository.WishlistRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class BookInteractionService {
    private final BookRepository bookRepository;
    private final BookCopyRepository bookCopyRepository;
    private final ReviewRepository reviewRepository;
    private final WishlistRepository wishlistRepository;
    private final UserRepository userRepository;
    private final LoanDetailRepository loanDetailRepository;

    public BookInteractionService(
            BookRepository bookRepository,
            BookCopyRepository bookCopyRepository,
            ReviewRepository reviewRepository,
            WishlistRepository wishlistRepository,
            UserRepository userRepository,
            LoanDetailRepository loanDetailRepository) {
        this.bookRepository = bookRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.reviewRepository = reviewRepository;
        this.wishlistRepository = wishlistRepository;
        this.userRepository = userRepository;
        this.loanDetailRepository = loanDetailRepository;
    }

    @Transactional(readOnly = true)
    public BookReviewSummaryDTO getReviews(Long bookId, UUID currentUserId) {
        requireBook(bookId);
        List<ReviewEntity> entities = reviewRepository.findByBookIdOrderByUpdatedAtDesc(bookId);
        Map<UUID, String> reviewerNames = userRepository.findAllById(
                        entities.stream().map(ReviewEntity::getUserId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(
                        user -> user.getUserId(),
                        user -> user.getFullName() == null ? "Bạn đọc HUIT" : user.getFullName()));
        List<BookReviewDTO> reviews = entities.stream()
                .map(review -> toReview(review, reviewerNames.get(review.getUserId()), currentUserId))
                .toList();
        BookReviewDTO myReview = reviews.stream().filter(BookReviewDTO::isMine).findFirst().orElse(null);
        boolean hasBorrowed = currentUserId != null
                && loanDetailRepository.countBorrowedItemsByUserIdAndBookId(currentUserId, bookId) > 0;
        boolean reviewEligible = currentUserId != null && myReview == null && hasBorrowed;
        return BookReviewSummaryDTO.builder()
                .bookId(bookId)
                .averageRating(round(reviewRepository.averageRatingByBookId(bookId)))
                .reviewCount(entities.size())
                .myReview(myReview)
                .reviewEligible(reviewEligible)
                .reviewEligibilityMessage(reviewEligibilityMessage(currentUserId, myReview, hasBorrowed))
                .reviews(reviews)
                .build();
    }

    @Transactional
    public BookReviewSummaryDTO saveReview(Long bookId, UUID userId, BookReviewRequestDTO request) {
        requireBook(bookId);
        if (reviewRepository.findByBookIdAndUserId(bookId, userId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bạn đã đánh giá tài liệu này. Mỗi bạn đọc chỉ được gửi một đánh giá cho mỗi đầu sách");
        }
        if (loanDetailRepository.countBorrowedItemsByUserIdAndBookId(userId, bookId) == 0) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bạn chỉ có thể đánh giá sau khi tài liệu đã được thủ thư giao mượn");
        }
        ReviewEntity review = new ReviewEntity();
        review.setBookId(bookId);
        review.setUserId(userId);
        review.setRating(request.getRating());
        review.setComment(normalizeComment(request.getComment()));
        reviewRepository.save(review);
        return getReviews(bookId, userId);
    }

    @Transactional
    public void deleteReview(Long bookId, UUID userId) {
        ReviewEntity review = reviewRepository.findByBookIdAndUserId(bookId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bạn chưa đánh giá tài liệu này"));
        reviewRepository.delete(review);
    }

    @Transactional(readOnly = true)
    public List<WishlistItemDTO> getWishlist(UUID userId) {
        List<WishlistEntity> saved = wishlistRepository.findByUserIdOrderByAddedAtDesc(userId);
        List<Long> bookIds = saved.stream()
                .map(WishlistEntity::getBookId)
                .map(this::parseBookId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, BookEntity> books = bookRepository.findAllById(bookIds).stream()
                .collect(Collectors.toMap(BookEntity::getBookId, Function.identity()));
        return saved.stream()
                .map(entry -> toWishlistItem(entry, books.get(parseBookId(entry.getBookId()))))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public WishlistItemDTO addToWishlist(UUID userId, Long bookId) {
        BookEntity book = requireBook(bookId);
        WishlistEntity entry = wishlistRepository.findByUserIdAndBookId(userId, bookId.toString())
                .orElseGet(() -> {
                    WishlistEntity created = new WishlistEntity();
                    created.setUserId(userId);
                    created.setBookId(bookId.toString());
                    created.setAddedAt(LocalDateTime.now());
                    return wishlistRepository.save(created);
                });
        return toWishlistItem(entry, book);
    }

    @Transactional
    public void removeFromWishlist(UUID userId, Long bookId) {
        if (wishlistRepository.deleteByUserIdAndBookId(userId, bookId.toString()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tài liệu chưa có trong tủ sách cá nhân");
        }
    }

    @Transactional(readOnly = true)
    public boolean isSaved(UUID userId, Long bookId) {
        return wishlistRepository.existsByUserIdAndBookId(userId, bookId.toString());
    }

    private BookEntity requireBook(Long bookId) {
        return bookRepository.findById(bookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đầu sách #" + bookId));
    }

    private BookReviewDTO toReview(ReviewEntity review, String reviewerName, UUID currentUserId) {
        return BookReviewDTO.builder()
                .reviewId(review.getReviewId())
                .reviewerName(reviewerName == null || reviewerName.isBlank() ? "Bạn đọc HUIT" : reviewerName)
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .mine(currentUserId != null && currentUserId.equals(review.getUserId()))
                .build();
    }

    private WishlistItemDTO toWishlistItem(WishlistEntity entry, BookEntity book) {
        if (book == null) {
            return null;
        }
        long available = bookCopyRepository.countByBookIdAndStatus(book.getBookId(), "AVAILABLE");
        return WishlistItemDTO.builder()
                .wishlistId(entry.getWishlistId())
                .bookId(book.getBookId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .category(book.getCategory() == null ? null : book.getCategory().getName())
                .cover(book.getImageUrlL() != null ? book.getImageUrlL() : book.getImageUrlM())
                .availableCopies(available)
                .addedAt(entry.getAddedAt())
                .build();
    }

    private Long parseBookId(String value) {
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String normalizeComment(String comment) {
        return comment == null || comment.isBlank() ? null : comment.trim();
    }

    private double round(Double value) {
        return value == null ? 0.0 : Math.round(value * 10.0) / 10.0;
    }

    private String reviewEligibilityMessage(UUID currentUserId, BookReviewDTO myReview, boolean hasBorrowed) {
        if (currentUserId == null) {
            return "Đăng nhập để đánh giá tài liệu";
        }
        if (myReview != null) {
            return "Bạn đã đánh giá tài liệu này. Mỗi bạn đọc chỉ được gửi một đánh giá";
        }
        if (!hasBorrowed) {
            return "Bạn chỉ có thể đánh giá sau khi tài liệu đã được thủ thư giao mượn";
        }
        return null;
    }
}
