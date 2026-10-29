package com.huit.library.modules.notification.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffNotificationServiceTest {
    @Test
    void createsOneOwnedNotificationForEveryStaffAccount() {
        UserRepository userRepository = mock(UserRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        UserEntity admin = staff("ADMIN");
        UserEntity librarian = staff("LIBRARIAN");
        when(userRepository.findStaffByRoleIds(List.of("ADMIN", "LIBRARIAN")))
                .thenReturn(List.of(admin, librarian));
        when(notificationService.createOnce(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(true);
        StaffNotificationService service = new StaffNotificationService(userRepository, notificationService);
        LocalDateTime now = LocalDateTime.now();

        int created = service.notifyAll(
                "LOAN_REQUEST_PENDING", "Có phiếu mới", "LOAN_TICKET", "12", "loan:12", now);

        assertThat(created).isEqualTo(2);
        verify(notificationService).createOnce(
                eq(admin.getUserId()), eq("LOAN_REQUEST_PENDING"), eq("Có phiếu mới"),
                eq("LOAN_TICKET"), eq("12"), eq("loan:12:" + admin.getUserId()), eq(now));
        verify(notificationService).createOnce(
                eq(librarian.getUserId()), eq("LOAN_REQUEST_PENDING"), eq("Có phiếu mới"),
                eq("LOAN_TICKET"), eq("12"), eq("loan:12:" + librarian.getUserId()), eq(now));
    }

    private UserEntity staff(String role) {
        UserEntity user = new UserEntity();
        user.setUserId(UUID.randomUUID());
        user.setRoleId(role);
        return user;
    }
}
