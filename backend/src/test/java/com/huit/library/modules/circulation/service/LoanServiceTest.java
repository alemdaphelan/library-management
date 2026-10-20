package com.huit.library.modules.circulation.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.circulation.dto.LoanTicketDetailDTO;
import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.LoanTicketRepository;
import com.huit.library.modules.circulation.repository.WaitlistRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoanServiceTest {
    private LoanTicketRepository ticketRepository;
    private LoanDetailRepository detailRepository;
    private BookCopyRepository copyRepository;
    private BookRepository bookRepository;
    private UserRepository userRepository;
    private WaitlistRepository waitlistRepository;
    private LoanService service;

    @BeforeEach
    void setUp() {
        ticketRepository = mock(LoanTicketRepository.class);
        detailRepository = mock(LoanDetailRepository.class);
        copyRepository = mock(BookCopyRepository.class);
        bookRepository = mock(BookRepository.class);
        userRepository = mock(UserRepository.class);
        waitlistRepository = mock(WaitlistRepository.class);
        service = new LoanService(
                ticketRepository,
                detailRepository,
                waitlistRepository,
                copyRepository,
                bookRepository,
                userRepository,
                mock(CirculationStateService.class));
    }

    @Test
    void getLoanReturnsReaderAndNormalizedLegacyValues() {
        UUID userId = UUID.randomUUID();
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setTicketId(21L);
        ticket.setUserId(userId);
        ticket.setTicketType("NORMAL");
        ticket.setStatus("PENDING");

        UserEntity reader = new UserEntity();
        reader.setUserId(userId);
        reader.setMssv("2001234567");
        reader.setFullName("Nguyễn Văn A");

        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setDetailId(31L);
        detail.setTicketId(21L);
        detail.setBarcode("BC-021");

        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode("BC-021");
        copy.setBookId(41L);

        BookEntity book = new BookEntity();
        book.setBookId(41L);
        book.setTitle("Clean Architecture");
        book.setAuthor("Robert C. Martin");

        when(ticketRepository.findById(21L)).thenReturn(Optional.of(ticket));
        when(userRepository.findById(userId)).thenReturn(Optional.of(reader));
        when(detailRepository.findByTicketId(21L)).thenReturn(List.of(detail));
        when(copyRepository.findById("BC-021")).thenReturn(Optional.of(copy));
        when(bookRepository.findById(41L)).thenReturn(Optional.of(book));

        LoanTicketDetailDTO result = service.getLoan(21L);

        assertThat(result.getMssv()).isEqualTo("2001234567");
        assertThat(result.getReaderName()).isEqualTo("Nguyễn Văn A");
        assertThat(result.getTicketType()).isEqualTo("BORROW");
        assertThat(result.getStatus()).isEqualTo("PENDING");
        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getTicketType()).isEqualTo("BORROW");
            assertThat(item.getBookTitle()).isEqualTo("Clean Architecture");
            assertThat(item.getBarcode()).isEqualTo("BC-021");
        });
    }

    @Test
    void renewExtendsDueDateByTenDays() {
        UUID userId = UUID.randomUUID();
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setTicketId(22L);
        ticket.setUserId(userId);
        ticket.setTicketType("BORROW");
        ticket.setStatus("BORROWING");
        LocalDateTime originalDueDate = LocalDateTime.now().plusDays(2);
        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setTicketId(22L);
        detail.setBarcode("BC-022");
        detail.setReturnStatus("BORROWING");
        detail.setDueDate(originalDueDate);
        detail.setRenewalCount(0);

        when(ticketRepository.findByIdForUpdate(22L)).thenReturn(Optional.of(ticket));
        when(detailRepository.findByTicketId(22L)).thenReturn(List.of(detail));
        when(copyRepository.findById("BC-022")).thenReturn(Optional.empty());

        service.renewLoan(22L, userId);

        assertThat(detail.getRenewalCount()).isEqualTo(1);
        assertThat(detail.getDueDate()).isEqualTo(originalDueDate.plusDays(10));
    }

    @Test
    void renewRejectsOverdueItem() {
        UUID userId = UUID.randomUUID();
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setTicketId(23L);
        ticket.setUserId(userId);
        ticket.setStatus("BORROWING");
        LoanDetailEntity detail = new LoanDetailEntity();
        detail.setTicketId(23L);
        detail.setReturnStatus("BORROWING");
        detail.setDueDate(LocalDateTime.now().minusMinutes(1));

        when(ticketRepository.findByIdForUpdate(23L)).thenReturn(Optional.of(ticket));
        when(detailRepository.findByTicketId(23L)).thenReturn(List.of(detail));

        assertThatThrownBy(() -> service.renewLoan(23L, userId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("quá hạn");
        verify(detailRepository, never()).save(detail);
    }

    @Test
    void getStaffLoansBuildsRealSummaryFromTicketDetails() {
        UUID userId = UUID.randomUUID();
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setTicketId(24L);
        ticket.setUserId(userId);
        ticket.setTicketType("NORMAL");
        ticket.setStatus("OVERDUE");

        UserEntity reader = new UserEntity();
        reader.setUserId(userId);
        reader.setMssv("2001240001");
        reader.setFullName("Trần Thị B");
        reader.setDepartment("Công nghệ thông tin");
        reader.setCardStatus("ACTIVE");

        LoanDetailEntity overdue = new LoanDetailEntity();
        overdue.setTicketId(24L);
        overdue.setDueDate(LocalDateTime.now().minusDays(3));
        overdue.setLateFineAccrued(new BigDecimal("3000"));

        LoanDetailEntity current = new LoanDetailEntity();
        current.setTicketId(24L);
        current.setDueDate(LocalDateTime.now().plusDays(2));
        current.setLateFineAccrued(BigDecimal.ZERO);

        when(ticketRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(ticket));
        when(userRepository.findById(userId)).thenReturn(Optional.of(reader));
        when(detailRepository.findByTicketId(24L)).thenReturn(List.of(overdue, current));

        var results = service.getStaffLoans();

        assertThat(results).singleElement().satisfies(summary -> {
            assertThat(summary.getTicketId()).isEqualTo(24L);
            assertThat(summary.getMssv()).isEqualTo("2001240001");
            assertThat(summary.getReaderName()).isEqualTo("Trần Thị B");
            assertThat(summary.getDepartment()).isEqualTo("Công nghệ thông tin");
            assertThat(summary.getTicketType()).isEqualTo("BORROW");
            assertThat(summary.getStatus()).isEqualTo("OVERDUE");
            assertThat(summary.getItemCount()).isEqualTo(2);
            assertThat(summary.getOverdueItemCount()).isEqualTo(1);
            assertThat(summary.getTotalLateFine()).isEqualByComparingTo("3000");
        });
    }
}
