package com.huit.library.modules.dashboard.service;

import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.circulation.repository.TrendingBookProjection;
import com.huit.library.modules.dashboard.dto.DashboardStatsDTO;
import com.huit.library.modules.dashboard.dto.TrendingBookDTO;
import com.huit.library.modules.dashboard.dto.WeeklyStatDTO;
import com.huit.library.modules.dashboard.repository.FinanceAnalyticsRepository;
import com.huit.library.modules.dashboard.repository.CirculationAnalyticsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {
    private BookCopyRepository bookCopyRepository;
    private LoanTicketRepository loanTicketRepository;
    private LoanDetailRepository loanDetailRepository;
    private FinanceAnalyticsRepository financeAnalytics;
    private DashboardService service;
    private CirculationAnalyticsRepository analytics;

    @BeforeEach
    void setUp() {
        bookCopyRepository = mock(BookCopyRepository.class);
        loanTicketRepository = mock(LoanTicketRepository.class);
        loanDetailRepository = mock(LoanDetailRepository.class);
        financeAnalytics = mock(FinanceAnalyticsRepository.class);
        analytics = mock(CirculationAnalyticsRepository.class);
        service = new DashboardService(
                bookCopyRepository,
                mock(BookRepository.class),
                loanTicketRepository,
                loanDetailRepository,
                mock(UserRepository.class),
                financeAnalytics, analytics);
    }

    @Test
    void statsComeFromRepositoriesInsteadOfHardCodedValues() {
        when(bookCopyRepository.count()).thenReturn(20L);
        when(bookCopyRepository.countByStatus("AVAILABLE")).thenReturn(12L);
        when(bookCopyRepository.countByStatus("BORROWED")).thenReturn(5L);
        when(bookCopyRepository.countByStatus("RESERVED")).thenReturn(3L);
        when(analytics.currentOverdueCount(any(LocalDateTime.class))).thenReturn(2L);
        when(financeAnalytics.lifetimeMoneyReceived()).thenReturn(new BigDecimal("15000"));

        DashboardStatsDTO result = service.getStats();

        assertThat(result.totalCopies()).isEqualTo(20);
        assertThat(result.availableCopies()).isEqualTo(12);
        assertThat(result.borrowedCurrent()).isEqualTo(5);
        assertThat(result.reservedCopies()).isEqualTo(3);
        assertThat(result.overdueBooks()).isEqualTo(2);
        assertThat(result.fineCollected()).isEqualByComparingTo("15000");
    }

    @Test
    void weeklyStatsAlwaysReturnSevenDaysAndRealCounts() {
        when(analytics.dailyBorrowCounts(any(LocalDate.class),any(LocalDate.class))).thenReturn(java.util.Map.of(LocalDate.now(),2L));

        List<WeeklyStatDTO> result = service.getWeeklyStats();

        assertThat(result).hasSize(7);
        assertThat(result.get(6).date()).isEqualTo(LocalDate.now());
        assertThat(result.get(6).count()).isEqualTo(2);
        assertThat(result.get(6).percentage()).isEqualTo(100);
    }

    @Test
    void trendingBooksAreMappedFromDatabaseProjection() {
        TrendingBookProjection projection = mock(TrendingBookProjection.class);
        when(projection.getBookId()).thenReturn(8L);
        when(projection.getTitle()).thenReturn("Clean Code");
        when(projection.getAuthor()).thenReturn("Robert C. Martin");
        when(projection.getBorrows()).thenReturn(6L);
        when(loanDetailRepository.findTrendingBooks(any(Pageable.class))).thenReturn(List.of(projection));

        List<TrendingBookDTO> result = service.getTrendingBooks();

        assertThat(result).containsExactly(new TrendingBookDTO(8L, "Clean Code", "Robert C. Martin", 6L));
    }
}
