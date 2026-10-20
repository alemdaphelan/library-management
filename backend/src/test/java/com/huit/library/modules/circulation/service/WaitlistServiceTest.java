package com.huit.library.modules.circulation.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookCopyEntity;
import com.huit.library.modules.catalog.repository.BookCopyRepository;
import com.huit.library.modules.circulation.entity.HolidayCalendarEntity;
import com.huit.library.modules.circulation.entity.WaitlistEntity;
import com.huit.library.modules.circulation.repository.HolidayCalendarRepository;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.circulation.repository.WaitlistRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WaitlistServiceTest {
    private WaitlistRepository waitlists;
    private BookCopyRepository copies;
    private HolidayCalendarRepository holidays;
    private UserRepository users;
    private WalletRepository wallets;
    private LoanDetailRepository details;
    private NotificationService notifications;
    private WaitlistService service;

    @BeforeEach
    void setUp() {
        waitlists = mock(WaitlistRepository.class);
        copies = mock(BookCopyRepository.class);
        holidays = mock(HolidayCalendarRepository.class);
        users = mock(UserRepository.class);
        wallets = mock(WalletRepository.class);
        details = mock(LoanDetailRepository.class);
        notifications = mock(NotificationService.class);
        service = new WaitlistService(waitlists, copies, holidays, users, wallets, details,
                notifications, "07:30", "17:00");
    }

    @Test
    void promotesFirstEligibleReaderForFortyEightHours() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setCardStatus("ACTIVE");
        WaitlistEntity entry = new WaitlistEntity();
        entry.setWaitlistId(10L);
        entry.setBookId(20L);
        entry.setUserId(userId);
        entry.setStatus("WAITING");
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode("BC-WAIT");
        copy.setBookId(20L);
        copy.setStatus("AVAILABLE");
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 9, 0);

        when(waitlists.findFirstByBookIdAndStatusOrderByCreatedAtAsc(20L, "WAITING"))
                .thenReturn(Optional.of(entry));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(wallets.findByUserId(userId)).thenReturn(Optional.empty());
        when(details.countOverdueItemsByUserId(userId, now)).thenReturn(0L);
        when(holidays.findById(anyString())).thenReturn(Optional.empty());

        assertThat(service.promoteNext(copy, now)).contains(entry);
        assertThat(entry.getStatus()).isEqualTo("NOTIFIED");
        assertThat(entry.getExpiresAt()).isEqualTo(now.plusHours(48));
        assertThat(copy.getStatus()).isEqualTo("RESERVED");
        assertThat(copy.getLockedByUser()).isEqualTo(userId);
    }

    @Test
    void extendsExpiryToNextOpeningWhenFortyEightHoursEndsOnClosedDay() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setCardStatus("ACTIVE");
        WaitlistEntity entry = new WaitlistEntity();
        entry.setWaitlistId(11L);
        entry.setBookId(21L);
        entry.setUserId(userId);
        BookCopyEntity copy = new BookCopyEntity();
        copy.setBarcode("BC-HOLIDAY");
        copy.setBookId(21L);
        LocalDateTime now = LocalDateTime.of(2026, 10, 19, 16, 0);
        HolidayCalendarEntity closed = new HolidayCalendarEntity();
        closed.setIsClosed(true);

        when(waitlists.findFirstByBookIdAndStatusOrderByCreatedAtAsc(21L, "WAITING"))
                .thenReturn(Optional.of(entry));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(wallets.findByUserId(userId)).thenReturn(Optional.empty());
        when(details.countOverdueItemsByUserId(userId, now)).thenReturn(0L);
        when(holidays.findById("2026-10-21")).thenReturn(Optional.of(closed));
        when(holidays.findById("2026-10-22")).thenReturn(Optional.empty());

        service.promoteNext(copy, now);

        assertThat(entry.getExpiresAt()).isEqualTo(LocalDateTime.of(2026, 10, 22, 7, 30));
    }
}
