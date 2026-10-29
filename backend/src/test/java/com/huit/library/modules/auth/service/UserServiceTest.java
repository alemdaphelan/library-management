package com.huit.library.modules.auth.service;

import com.huit.library.modules.auth.dto.StudentUpsertRequestDTO;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        UserService service = new UserService(repository, encoder, walletRepository, new BigDecimal("50000"));

        UserEntity result = service.createStudent(request("20012345", "student@huit.edu.vn"));

        assertThat(result.getRoleId()).isEqualTo("STUDENT");
        assertThat(result.getCardStatus()).isEqualTo("ACTIVE");
        assertThat(result.getPasswordHash()).isEqualTo("encoded");
        assertThat(result.getIsFirstLogin()).isTrue();
        assertThat(result.getCardExpiryDate()).isNotNull();
        verify(walletRepository).save(argThat(wallet ->
                wallet.getUserId().equals(result.getUserId())
                        && wallet.getDepositBalance().compareTo(new BigDecimal("50000")) == 0
                        && wallet.getTotalDebt().signum() == 0));
    }

    @Test
    void createStudentRejectsDuplicateMssv() {
        UserRepository repository = mock(UserRepository.class);
        when(repository.findByMssv("20012345")).thenReturn(Optional.of(new UserEntity()));
        UserService service = new UserService(
                repository, mock(PasswordEncoder.class), mock(WalletRepository.class), new BigDecimal("50000"));

        assertThatThrownBy(() -> service.createStudent(request("20012345", "student@huit.edu.vn")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("MSSV đã tồn tại");
        verify(repository, never()).save(any());
    }

    private StudentUpsertRequestDTO request(String mssv, String email) {
        return new StudentUpsertRequestDTO(
                mssv, "Nguyễn Văn A", email, "CNTT", "0901234567", "ACTIVE", null);
    }
}
