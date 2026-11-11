package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.penalty.repository.CompensationRateRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class BookValuationServiceTest {
    private final CompensationRateRepository rates = mock(CompensationRateRepository.class);

    private BookValuationService demo(String... profiles) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new BookValuationService(rates, true, environment);
    }

    @Test
    void demoValuesAreStableCheapAndDoNotRewriteFallbackCatalog() {
        var service = demo("dev");
        java.util.Set<BigDecimal> prices = new java.util.HashSet<>();
        for (long id = 1; id <= 100; id++) {
            var book = book(id, "CONFIGURED_FALLBACK");
            var value = service.resolve(book);
            assertThat(value.amount()).isBetween(new BigDecimal("15000"), new BigDecimal("65000"));
            assertThat(value).isEqualTo(service.resolve(book));
            assertThat(value.source()).isEqualTo("DEMO_GENERATED");
            assertThat(book.getDefaultPrice()).isEqualByComparingTo("150000");
            assertThat(book.getPriceSource()).isEqualTo("CONFIGURED_FALLBACK");
            prices.add(value.amount());
        }
        assertThat(prices.size()).isGreaterThan(1);
        verifyNoInteractions(rates);
    }

    @Test
    void manualPricesAndDigitalValuesArePreserved() {
        var service = demo("test");
        assertThat(service.resolve(book(1L, "CATALOG")).amount()).isEqualByComparingTo("150000");
        var digital = book(2L, "CATALOG");
        digital.setIsDigital(true);
        assertThat(service.resolve(digital).amount()).isZero();
        assertThat(service.resolve(digital).source()).isEqualTo("DIGITAL");
    }

    @Test
    void defaultModeKeepsConfiguredFallbackAndDemoIsExplicitlyRestricted() {
        assertThat(
                        new BookValuationService(rates)
                                .resolve(book(1L, "CONFIGURED_FALLBACK"))
                                .amount())
                .isEqualByComparingTo("150000");
        assertThatThrownBy(() -> demo("prod")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> demo("dev", "production"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> demo()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void threeDemoBooksFitAnEligibleWalletButFiftyThousandStillFailsMinimum() {
        var details =
                mock(com.huit.library.modules.circulation.repository.LoanDetailRepository.class);
        var wallets = mock(com.huit.library.modules.finance.repository.WalletRepository.class);
        var books = mock(com.huit.library.modules.catalog.repository.BookRepository.class);
        var policy = mock(com.huit.library.modules.policy.service.LibraryPolicyService.class);
        var reader = java.util.UUID.randomUUID();
        var wallet = new com.huit.library.modules.finance.entity.WalletEntity();
        wallet.setDepositBalance(new BigDecimal("100000"));
        when(wallets.findByUserId(reader)).thenReturn(java.util.Optional.of(wallet));
        when(details.findOpenBookIdsByUserId(reader)).thenReturn(java.util.List.of());
        when(books.findAllById(any()))
                .thenReturn(
                        java.util.List.of(
                                book(1L, "CONFIGURED_FALLBACK"),
                                book(2L, "CONFIGURED_FALLBACK"),
                                book(3L, "CONFIGURED_FALLBACK")));
        when(policy.minimumSecurityDeposit()).thenReturn(new BigDecimal("100000"));
        when(policy.borrowingValueMargin()).thenReturn(new BigDecimal("50000"));
        var service =
                new com.huit.library.modules.circulation.service.BorrowingValuePolicyService(
                        details, wallets, books, policy, demo("dev"));
        var snapshot = service.getSnapshot(reader, java.util.List.of(1L, 2L, 3L));
        assertThat(snapshot.eligible()).isTrue();
        assertThat(snapshot.requestedBookValue())
                .isLessThanOrEqualTo(snapshot.maximumBorrowingValue());
        assertThat(wallet.getDepositBalance()).isEqualByComparingTo("100000");
        wallet.setDepositBalance(new BigDecimal("50000"));
        assertThat(service.getSnapshot(reader, java.util.List.of(1L, 2L, 3L)).depositEligible())
                .isFalse();
        assertThat(wallet.getDepositBalance()).isEqualByComparingTo("50000");
        verify(wallets, never()).save(any());
    }

    private BookEntity book(long id, String source) {
        var book = new BookEntity();
        book.setBookId(id);
        book.setTitle("Demo book");
        book.setDefaultPrice(new BigDecimal("150000"));
        book.setPriceStatus("ESTIMATED");
        book.setPriceSource(source);
        return book;
    }
}
