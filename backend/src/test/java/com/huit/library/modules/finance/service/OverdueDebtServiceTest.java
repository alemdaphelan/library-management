package com.huit.library.modules.finance.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.finance.entity.TransactionEntity;
import com.huit.library.modules.finance.repository.TransactionRepository;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.policy.service.LibraryPolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OverdueDebtServiceTest {
    private TransactionRepository transactionRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private LibraryPolicyService policyService;
    private OverdueDebtService service;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        transactionRepository = mock(TransactionRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        policyService = mock(LibraryPolicyService.class);
        service = new OverdueDebtService(
                transactionRepository, userRepository, notificationService, policyService);
        now = LocalDateTime.of(2026, 11, 3, 8, 0);
        when(policyService.overdueDebtProcessingDays()).thenReturn(30);
    }

    @Test
    void locksBorrowingRightsAndMarksDebtProcessedAfterThirtyDays() {
        UUID userId = UUID.randomUUID();
        TransactionEntity debt = new TransactionEntity();
        debt.setTransactionId(10L);
        debt.setUserId(userId);
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setCardStatus("ACTIVE");

        when(transactionRepository.findUnprocessedOverdueDebtForUpdate(now.minusDays(30)))
                .thenReturn(List.of(debt));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        int processed = service.processAt(now);

        assertThat(processed).isEqualTo(1);
        assertThat(debt.getOverdueProcessedAt()).isEqualTo(now);
        assertThat(user.getCardStatus()).isEqualTo("LOCKED");
        assertThat(user.getCirculationLockReason())
                .isEqualTo(OverdueDebtService.OVERDUE_DEBT_LOCK_REASON);
        assertThat(user.getCirculationLockedUntil()).isNull();
        verify(transactionRepository).save(debt);
        verify(userRepository).save(user);
        verify(notificationService).createOnce(
                eq(userId), eq("DEBT_OVERDUE"), anyString(),
                eq("USER"), eq(userId.toString()), anyString(), eq(now));
    }

    @Test
    void doesNothingWhenNoDebtReachedProcessingDeadline() {
        when(transactionRepository.findUnprocessedOverdueDebtForUpdate(now.minusDays(30)))
                .thenReturn(List.of());

        assertThat(service.processAt(now)).isZero();

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
