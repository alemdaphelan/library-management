package com.huit.library.modules.finance.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.finance.entity.TransactionEntity;
import com.huit.library.modules.finance.repository.TransactionRepository;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.policy.service.LibraryPolicyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OverdueDebtService {
    public static final String OVERDUE_DEBT_LOCK_REASON = "OVERDUE_DEBT";

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final LibraryPolicyService policyService;

    public OverdueDebtService(
            TransactionRepository transactionRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            LibraryPolicyService policyService) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.policyService = policyService;
    }

    @Transactional
    public int processAt(LocalDateTime now) {
        int processingDays = policyService.overdueDebtProcessingDays();
        LocalDateTime cutoff = now.minusDays(processingDays);
        List<TransactionEntity> overdueEntries =
                transactionRepository.findUnprocessedOverdueDebtForUpdate(cutoff);
        if (overdueEntries.isEmpty()) {
            return 0;
        }

        Set<UUID> affectedUsers = new LinkedHashSet<>();
        for (TransactionEntity entry : overdueEntries) {
            entry.setOverdueProcessedAt(now);
            transactionRepository.save(entry);
            if (entry.getUserId() != null) {
                affectedUsers.add(entry.getUserId());
            }
        }

        int lockedUsers = 0;
        for (UUID userId : affectedUsers) {
            UserEntity user = userRepository.findByIdForUpdate(userId).orElse(null);
            if (user == null) {
                continue;
            }
            if (user.hasLibraryCard()) {
                user.setCardStatus("LOCKED");
            }
            user.setCirculationLockedUntil(null);
            user.setCirculationLockReason(OVERDUE_DEBT_LOCK_REASON);
            userRepository.save(user);
            lockedUsers++;

            notificationService.createOnce(
                    userId,
                    "DEBT_OVERDUE",
                    "Công nợ thư viện đã quá " + processingDays
                            + " ngày. Quyền mượn, đặt giữ và gia hạn bị khóa cho đến khi "
                            + "thanh toán hết công nợ và hoàn trả đủ tài liệu.",
                    "USER",
                    userId.toString(),
                    "DEBT_OVERDUE:" + userId + ":" + now.toLocalDate(),
                    now);
        }
        return lockedUsers;
    }
}
