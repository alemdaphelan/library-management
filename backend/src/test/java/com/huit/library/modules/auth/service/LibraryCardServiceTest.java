package com.huit.library.modules.auth.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.WalletRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LibraryCardServiceTest {
    @Test
    void returnsCardIdentityStatusDepositAndDebtFromPersistedData() {
        UserRepository userRepository = mock(UserRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setMssv("20012345");
        user.setFullName("Nguyễn Văn A");
        user.setDepartment("Công nghệ thông tin");
        user.setCardStatus("ACTIVE");
        user.setCardExpiryDate(LocalDateTime.of(2027, 10, 23, 0, 0));
        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(userId);
        wallet.setDepositBalance(new BigDecimal("50000"));
        wallet.setTotalDebt(new BigDecimal("1000"));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        var result = new LibraryCardService(userRepository, walletRepository).get(userId);

        assertThat(result.cardNumber()).isEqualTo("20012345");
        assertThat(result.cardStatus()).isEqualTo("ACTIVE");
        assertThat(result.depositBalance()).isEqualByComparingTo("50000");
        assertThat(result.totalDebt()).isEqualByComparingTo("1000");
    }
}
