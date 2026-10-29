package com.huit.library.modules.penalty.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.penalty.repository.OverdueViolationRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OverdueViolationServiceTest {
    @Test
    void thirdOverdueInAcademicYearLocksCirculationForThreeCalendarMonths() {
        OverdueViolationRepository violations = mock(OverdueViolationRepository.class);
        UserRepository users = mock(UserRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        OverdueViolationService service = new OverdueViolationService(
                violations, users, notifications, 8, 1);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        LoanTicketEntity ticket = new LoanTicketEntity();
        ticket.setTicketId(30L);
        ticket.setUserId(userId);
        LocalDateTime occurredAt = LocalDateTime.of(2026, 10, 18, 9, 0);

        when(users.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(violations.existsByTicketIdAndAcademicYear(30L, "2026-2027")).thenReturn(false);
        when(violations.countByUserIdAndAcademicYear(userId, "2026-2027")).thenReturn(3L);

        assertThat(service.recordFirstOverdue(ticket, occurredAt)).isTrue();
        assertThat(user.getOverdueViolationCount()).isEqualTo(3);
        assertThat(user.getCirculationLockedUntil()).isEqualTo(occurredAt.plusMonths(3));
        verify(notifications).createOnce(
                eq(userId), eq("CIRCULATION_LOCKED"), anyString(),
                eq("USER"), eq(userId.toString()), eq("CIRCULATION_LOCKED:2026-2027"), eq(occurredAt));
    }

    @Test
    void academicYearStartsOnFirstOfAugust() {
        OverdueViolationService service = new OverdueViolationService(
                mock(OverdueViolationRepository.class), mock(UserRepository.class),
                mock(NotificationService.class), 8, 1);

        assertThat(service.academicYear(LocalDate.of(2026, 7, 31))).isEqualTo("2025-2026");
        assertThat(service.academicYear(LocalDate.of(2026, 8, 1))).isEqualTo("2026-2027");
    }
}
