package com.huit.library.modules.interactive.service;

import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        requireUserId(userId);
        List<WishlistEntity> saved = wishlistRepository.findByUserIdOrderByAddedAtDesc(userId);
        List<WishlistEntity> deduplicated = new ArrayList<>();
        Set<Long> seenBookIds = new LinkedHashSet<>();
        for (WishlistEntity entry : saved) {
            Long parsedBookId = parseBookId(entry.getBookId());
            if (parsedBookId != null && seenBookIds.add(parsedBookId)) {
                deduplicated.add(entry);
            }
        }
        if (seenBookIds.isEmpty()) {
            return List.of();
        }
        Map<Long, BookEntity> books = bookRepository.findAllById(new ArrayList<>(seenBookIds)).stream()
                .collect(Collectors.toMap(BookEntity::getBookId, Function.identity(), (left, right) -> left));
        return deduplicated.stream()
                .map(entry -> toWishlistItem(entry, books.get(parseBookId(entry.getBookId()))))
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public WishlistItemDTO addToWishlist(UUID userId, Long bookId) {
        requireUserId(userId);
        requireValidBookId(bookId);
        BookEntity book = requireBook(bookId);
        String key = bookId.toString();
        WishlistEntity entry = findExistingAndCleanupDuplicates(userId, key);
        if (entry == null) {
            try {
                WishlistEntity created = new WishlistEntity();
                created.setUserId(userId);
                created.setBookId(key);
                created.setAddedAt(LocalDateTime.now());
                entry = wishlistRepository.save(created);
            } catch (DataIntegrityViolationException ex) {
                entry = findExistingAndCleanupDuplicates(userId, key);
                if (entry == null) {
                    throw ex;
                }
            }
        }
        return toWishlistItem(entry, book);
    }

    @Transactional
    public void removeFromWishlist(UUID userId, Long bookId) {
        requireUserId(userId);
        requireValidBookId(bookId);
        wishlistRepository.deleteByUserIdAndBookId(userId, bookId.toString());
    }

    @Transactional(readOnly = true)
    public boolean isSaved(UUID userId, Long bookId) {
        requireUserId(userId);
        requireValidBookId(bookId);
        return wishlistRepository.existsByUserIdAndBookId(userId, bookId.toString());
    }

    private WishlistEntity findExistingAndCleanupDuplicates(UUID userId, String bookId) {
        List<WishlistEntity> matches = wishlistRepository.findAllByUserIdAndBookIdOrderByAddedAtDesc(userId, bookId);
        if (matches != null && !matches.isEmpty()) {
            if (matches.size() > 1) {
                wishlistRepository.deleteAll(matches.subList(1, matches.size()));
            }
            return matches.get(0);
        }
        return wishlistRepository.findByUserIdAndBookId(userId, bookId).orElse(null);
    }

    private void requireUserId(UUID userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Vui lòng đăng nhập để sử dụng danh sách sách đã lưu");
        }
    }

    private void requireValidBookId(Long bookId) {
        if (bookId == null || bookId <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã đầu sách không hợp lệ");
        }
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
        List<BookCopyEntity> copyList = bookCopyRepository.findByBookId(book.getBookId());
        if (copyList == null) {
            copyList = List.of();
        }
        long availableFromList = copyList.stream()
                .filter(copy -> copy != null && "AVAILABLE".equalsIgnoreCase(copy.getStatus()))
                .count();
        long available = copyList.isEmpty()
                ? bookCopyRepository.countByBookIdAndStatus(book.getBookId(), "AVAILABLE")
                : availableFromList;
        long totalCopies = Math.max(copyList.size(), available);
        String location = resolveLocation(copyList);
        return WishlistItemDTO.builder()
                .wishlistId(entry.getWishlistId())
                .bookId(book.getBookId())
                .title(book.getTitle())
                .author(book.getAuthor())
                .category(book.getCategory() == null ? null : book.getCategory().getName())
                .department(resolveDepartment(book))
                .location(location.isBlank() ? null : location)
                .cover(resolveCover(book))
                .availableCopies(available)
                .copies(totalCopies)
                .status(available > 0 ? "available" : "out_of_stock")
                .isbn(book.getIsbn())
                .publisher(book.getPublisher())
                .publishYear(book.getPublishYear())
                .isDigital(book.getIsDigital())
                .addedAt(entry.getAddedAt())
                .build();
    }

    private String resolveCover(BookEntity book) {
        if (book.getImageUrlL() != null && !book.getImageUrlL().isBlank()) {
            return book.getImageUrlL();
        }
        if (book.getImageUrlM() != null && !book.getImageUrlM().isBlank()) {
            return book.getImageUrlM();
        }
        return book.getImageUrlS();
    }

    private String resolveLocation(List<BookCopyEntity> copies) {
        return copies.stream()
                .map(BookCopyEntity::getShelf)
                .filter(Objects::nonNull)
                .map(shelf -> {
                    var bookshelf = shelf.getBookshelf();
                    var floor = bookshelf == null ? null : bookshelf.getFloor();
                    return Stream.of(
                                    floor == null ? null : floor.getName(),
                                    bookshelf == null ? null : bookshelf.getName(),
                                    shelf.getName())
                            .filter(value -> value != null && !value.isBlank())
                            .collect(Collectors.joining(" / "));
                })
                .filter(value -> !value.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private String resolveDepartment(BookEntity book) {
        if (book.getBookId() != null) {
            List<String> names = bookRepository.findDepartmentNamesByBookId(book.getBookId());
            if (names != null && !names.isEmpty()) {
                String joined = names.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .distinct()
                        .limit(2)
                        .collect(Collectors.joining(", "));
                if (!joined.isBlank()) {
                    return joined;
                }
            }
        }
        if (book.getCategory() != null && book.getCategory().getName() != null
                && !book.getCategory().getName().isBlank()) {
            return book.getCategory().getName();
        }
        return null;
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
