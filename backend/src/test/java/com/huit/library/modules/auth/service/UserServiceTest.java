package com.huit.library.modules.auth.service;

import com.huit.library.modules.auth.dto.StudentUpsertRequestDTO;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class UserServiceTest {
    @Test
    void createStudentAppliesSafeDefaultsAndHashesMssvPassword() {
        UserRepository repository = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        when(encoder.encode("20012345")).thenReturn("encoded");
        when(repository.findByMssv("20012345")).thenReturn(Optional.empty());
        when(repository.findByEmail("student@huit.edu.vn")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setUserId(UUID.randomUUID());
            return user;
        });
        when(walletRepository.findByUserId(any())).thenReturn(Optional.empty());
        UserService service = new UserService(repository, encoder, walletRepository, new BigDecimal("100000"));

        UserEntity result = service.createStudent(request("20012345", "student@huit.edu.vn"));

        assertThat(result.getRoleId()).isEqualTo("STUDENT");
        assertThat(result.hasLibraryCard()).isFalse();
        assertThat(result.getPasswordHash()).isEqualTo("encoded");
        assertThat(result.getIsFirstLogin()).isTrue();
        assertThat(result.getCardExpiryDate()).isNull();
        verify(walletRepository).save(argThat(wallet ->
                wallet.getUserId().equals(result.getUserId())
                        && wallet.getDepositBalance().compareTo(new BigDecimal("100000")) == 0
                        && wallet.getTotalDebt().signum() == 0));
    }

    @Test
    void createStudentRejectsDuplicateMssv() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByMssv("20012345")).thenReturn(Optional.of(new UserEntity()));
        UserService service = new UserService(
                repository, mock(PasswordEncoder.class), mock(WalletRepository.class), new BigDecimal("100000"));

        assertThatThrownBy(() -> service.createStudent(request("20012345", "student@huit.edu.vn")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("MSSV đã tồn tại");
        verify(repository, never()).save(any());
    }

    @Test
    void updateStudentNotifiesReaderWhenCardStatusChangesToLockedAndBackToActive() {
        UserRepository repository = mock(UserRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        UUID userId = UUID.randomUUID();
        UserEntity existing = new UserEntity();
        existing.setUserId(userId);
        existing.setMssv("20012345");
        existing.setEmail("student@huit.edu.vn");
        existing.setCardStatus("ACTIVE");

        when(repository.findByMssv("20012345")).thenReturn(Optional.of(existing));
        when(repository.findByMssvAndUserType("20012345", "STUDENT")).thenReturn(Optional.of(existing));
        when(repository.findByEmail("student@huit.edu.vn")).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UserService service = new UserService(
                repository, mock(PasswordEncoder.class), mock(WalletRepository.class),
                notificationService, new BigDecimal("100000"));

        service.updateStudent("20012345",
                new StudentUpsertRequestDTO("20012345", "Nguyễn Văn A", "student@huit.edu.vn", "CNTT", "0901234567", "LOCKED", null));
        verify(notificationService).createOnce(
                eq(userId), eq("CIRCULATION_LOCKED"), anyString(), eq("USER"), eq(userId.toString()), anyString(), any());

        service.updateStudent("20012345",
                new StudentUpsertRequestDTO("20012345", "Nguyễn Văn A", "student@huit.edu.vn", "CNTT", "0901234567", "ACTIVE", null));
        verify(notificationService).createOnce(
                eq(userId), eq("CIRCULATION_UNLOCKED"), anyString(), eq("USER"), eq(userId.toString()), anyString(), any());
    }

    @Test
    void refusesFirstIssueViaStudentCrudButAllowsProfileUpdates() {
        UserRepository repository = mock(UserRepository.class);
        UserEntity pending = new UserEntity();
        pending.setUserId(UUID.randomUUID());
        when(repository.findByMssvAndUserType("20012345", "STUDENT")).thenReturn(Optional.of(pending));
        when(repository.save(pending)).thenReturn(pending);
        UserService service = new UserService(
                repository, mock(PasswordEncoder.class), mock(WalletRepository.class), BigDecimal.ZERO);
        var bypass = new StudentUpsertRequestDTO("20012345", "Nguyễn Văn A", "student@huit.edu.vn",
                "CNTT", null, "ACTIVE", null);

        assertThatThrownBy(() -> service.createStudent(bypass)).hasMessageContaining("điểm danh Có mặt");
        assertThatThrownBy(() -> service.updateStudent("20012345", bypass)).hasMessageContaining("điểm danh Có mặt");
        assertThat(service.updateStudent("20012345", request("20012345", "student@huit.edu.vn")).hasLibraryCard()).isFalse();
    }

    private StudentUpsertRequestDTO request(String mssv, String email) {
        return new StudentUpsertRequestDTO(
                mssv, "Nguyễn Văn A", email, "CNTT", "0901234567", null, null);
    }
}
