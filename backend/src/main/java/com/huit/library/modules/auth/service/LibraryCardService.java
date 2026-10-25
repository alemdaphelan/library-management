package com.huit.library.modules.auth.service;

import com.huit.library.modules.auth.dto.LibraryCardResponseDTO;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class LibraryCardService {
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    public LibraryCardService(UserRepository userRepository, WalletRepository walletRepository) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public LibraryCardResponseDTO get(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
        WalletEntity wallet = walletRepository.findByUserId(userId).orElse(null);
        boolean issued = user.getCardStatus() != null && !user.getCardStatus().isBlank();
        return new LibraryCardResponseDTO(
                issued,
                issued ? user.getMssv() : null,
                user.getFullName(),
                user.getDepartment(),
                user.getCardStatus(),
                issued ? user.getCreatedAt() : null,
                issued ? user.getCardExpiryDate() : null,
                wallet == null || wallet.getDepositBalance() == null
                        ? BigDecimal.ZERO : wallet.getDepositBalance(),
                wallet == null || wallet.getTotalDebt() == null
                        ? BigDecimal.ZERO : wallet.getTotalDebt(),
                user.getCirculationLockedUntil(),
                user.getCirculationLockReason());
    }
}
