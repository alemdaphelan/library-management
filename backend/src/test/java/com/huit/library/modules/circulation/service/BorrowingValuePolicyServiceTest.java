package com.huit.library.modules.circulation.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.catalog.service.BookValuationService;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.policy.service.LibraryPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BorrowingValuePolicyServiceTest {
    private LoanDetailRepository detailRepository;
    private WalletRepository walletRepository;
    private BookRepository bookRepository;
    private LibraryPolicyService policyService;
    private BookValuationService bookValuationService;
    private BorrowingValuePolicyService service;
    private UUID userId;
    private String requestedBookValue;

    @BeforeEach
    void setUp() {
        detailRepository = mock(LoanDetailRepository.class);
        walletRepository = mock(WalletRepository.class);
        bookRepository = mock(BookRepository.class);
        policyService = mock(LibraryPolicyService.class);
        bookValuationService = mock(BookValuationService.class);
        service = new BorrowingValuePolicyService(
                detailRepository, walletRepository, bookRepository, policyService, bookValuationService);
        userId = UUID.randomUUID();
        requestedBookValue = "50000";
        when(policyService.minimumSecurityDeposit()).thenReturn(new BigDecimal("100000"));
        when(policyService.borrowingValueMargin()).thenReturn(new BigDecimal("50000"));
        when(bookValuationService.resolve(any(BookEntity.class))).thenAnswer(invocation -> {
            BookEntity book = invocation.getArgument(0);
            return new BookValuationService.BookValue(
                    book.getDefaultPrice(), true, "TEST", "VND", null);
        });
    }

    @Test
    void rejectsBorrowingWhenSecurityDepositIsBelowMinimum() {
        when(walletRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(wallet("99999")));
        when(detailRepository.findOpenBookIdsByUserId(userId)).thenReturn(List.of());
        when(bookRepository.findAllById(any())).thenAnswer(invocation -> booksFor(invocation.getArgument(0)));

        assertThatThrownBy(() -> service.ensureCanAddBooks(userId, List.of(10L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("thấp hơn mức tối thiểu");
    }

    @Test
    void rejectsWhenProjectedBookValueExceedsDepositPlusMargin() {
        requestedBookValue = "50001";
        when(walletRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(wallet("100000")));
        when(detailRepository.findOpenBookIdsByUserId(userId)).thenReturn(List.of(20L));
        when(bookRepository.findAllById(any())).thenAnswer(invocation -> booksFor(invocation.getArgument(0)));

        assertThatThrownBy(() -> service.ensureCanAddBooks(userId, List.of(10L)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("vượt giới hạn");
    }

    @Test
    void allowsProjectedBookValueAtExactDepositPlusMarginBoundary() {
        when(walletRepository.findByUserIdForUpdate(userId))
                .thenReturn(Optional.of(wallet("100000")));
        when(detailRepository.findOpenBookIdsByUserId(userId)).thenReturn(List.of(20L));
        when(bookRepository.findAllById(any())).thenAnswer(invocation -> booksFor(invocation.getArgument(0)));

        service.ensureCanAddBooks(userId, List.of(10L));

        verify(detailRepository).findOpenBookIdsByUserId(userId);
    }

    private WalletEntity wallet(String deposit) {
        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(userId);
        wallet.setDepositBalance(new BigDecimal(deposit));
        return wallet;
    }

    private BookEntity book(Long id, String value) {
        BookEntity book = new BookEntity();
        book.setBookId(id);
        book.setDefaultPrice(new BigDecimal(value));
        return book;
    }

    private List<BookEntity> booksFor(Iterable<Long> ids) {
        java.util.ArrayList<BookEntity> books = new java.util.ArrayList<>();
        for (Long id : ids) {
            books.add(book(id, id.equals(20L) ? "100000" : requestedBookValue));
        }
        return books;
    }
}
