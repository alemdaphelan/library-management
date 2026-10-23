package com.huit.library.modules.circulation.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.notification.service.NotificationService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservationNoShowServiceTest {
    @Test
    void locksCirculationForThreeMonthsOnThirdNoShowInAcademicYear() {
        UserRepository users = mock(UserRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setReservationViolationAcademicYear("2026-2027");
        user.setReservationNoShowCount(2);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 23, 17, 1);
        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        ReservationNoShowService service = new ReservationNoShowService(
                users, notifications, 3, 3, 8, 1);

        int count = service.record(userId, occurredAt, "LOAN_TICKET", "10");

        assertThat(count).isEqualTo(3);
        assertThat(user.getCirculationLockedUntil()).isEqualTo(occurredAt.plusMonths(3));
        assertThat(user.getCirculationLockReason()).contains("3 lần");
        verify(notifications).createOnce(
                eq(userId), eq("CIRCULATION_LOCKED"), anyString(),
                eq("USER"), eq(userId.toString()),
                eq("RESERVATION_NO_SHOW_LOCK:2026-2027"), eq(occurredAt));
    }

    @Test
    void resetsCounterWhenAcademicYearChanges() {
        UserRepository users = mock(UserRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setReservationViolationAcademicYear("2025-2026");
        user.setReservationNoShowCount(2);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 23, 17, 1);
        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        ReservationNoShowService service = new ReservationNoShowService(
                users, notifications, 3, 3, 8, 1);

        assertThat(service.record(userId, occurredAt, "WAITLIST", "11")).isEqualTo(1);
        assertThat(user.getReservationViolationAcademicYear()).isEqualTo("2026-2027");
        assertThat(user.getCirculationLockedUntil()).isNull();
    }
}
