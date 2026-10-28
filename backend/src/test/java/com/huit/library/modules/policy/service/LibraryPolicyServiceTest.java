package com.huit.library.modules.policy.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.policy.dto.LibraryPolicyRequestDTO;
import com.huit.library.modules.policy.entity.LibraryPolicyEntity;
import com.huit.library.modules.policy.entity.LibraryPolicyHistoryEntity;
import com.huit.library.modules.policy.repository.LibraryPolicyHistoryRepository;
import com.huit.library.modules.policy.repository.LibraryPolicyRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LibraryPolicyServiceTest {
    @Test
    void updatesAllPolicyValuesAndAuditActor() {
        LibraryPolicyRepository repository = mock(LibraryPolicyRepository.class);
        LibraryPolicyHistoryRepository historyRepository = mock(LibraryPolicyHistoryRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        LibraryPolicyEntity existing = defaults();
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        LibraryPolicyService service = new LibraryPolicyService(repository, historyRepository, userRepository);
        LibraryPolicyRequestDTO request = new LibraryPolicyRequestDTO();
        request.setMaximumOpenItems(5);
        request.setHomeLoanDays(14);
        request.setRenewalDays(7);
        request.setMaximumRenewalCount(2);
        request.setWaitlistHoldHours(24);
        request.setHomeLateFinePerDay(new BigDecimal("2000"));
        request.setOnsiteLateFinePerDay(new BigDecimal("6000"));
        request.setTechnicalProcessingFee(new BigDecimal("60000"));
        request.setCompensationSettlementDays(45);
        UUID actor = UUID.randomUUID();

        var result = service.updatePolicy(request, actor);

        assertThat(result.getMaximumOpenItems()).isEqualTo(5);
        assertThat(result.getHomeLoanDays()).isEqualTo(14);
        assertThat(result.getMaximumRenewalCount()).isEqualTo(2);
        assertThat(result.getTechnicalProcessingFee()).isEqualByComparingTo("60000");
        assertThat(result.getUpdatedBy()).isEqualTo(actor);
        verify(repository).save(existing);
        verify(historyRepository).save(any(LibraryPolicyHistoryEntity.class));
    }

    @Test
    void exposesSafeDefaultsBeforeMigrationSeedIsAvailable() {
        LibraryPolicyRepository repository = mock(LibraryPolicyRepository.class);
        when(repository.findById(1L)).thenReturn(Optional.empty());
        LibraryPolicyService service = new LibraryPolicyService(
                repository,
                mock(LibraryPolicyHistoryRepository.class),
                mock(UserRepository.class));

        assertThat(service.maximumOpenItems()).isEqualTo(3);
        assertThat(service.homeLoanDays()).isEqualTo(10);
        assertThat(service.maximumRenewalCount()).isEqualTo(1);
        assertThat(service.homeLateFinePerDay()).isEqualByComparingTo("1000");
    }

    @Test
    void returnsNewestHistoryFirstWithActorInformation() {
        LibraryPolicyRepository repository = mock(LibraryPolicyRepository.class);
        LibraryPolicyHistoryRepository historyRepository = mock(LibraryPolicyHistoryRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        UUID actorId = UUID.randomUUID();
        LibraryPolicyHistoryEntity history = history(2L, actorId);
        UserEntity actor = new UserEntity();
        actor.setUserId(actorId);
        actor.setFullName("Admin Thư viện");
        actor.setEmail("admin@huit.edu.vn");
        when(historyRepository.findAllByOrderByChangedAtDescHistoryIdDesc()).thenReturn(List.of(history));
        when(userRepository.findAllById(List.of(actorId))).thenReturn(List.of(actor));
        LibraryPolicyService service = new LibraryPolicyService(repository, historyRepository, userRepository);

        var result = service.getHistory();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getHistoryId()).isEqualTo(2L);
        assertThat(result.get(0).getChangedByName()).isEqualTo("Admin Thư viện");
        assertThat(result.get(0).getChangedByEmail()).isEqualTo("admin@huit.edu.vn");
        assertThat(result.get(0).getMaximumRenewalCount()).isEqualTo(1);
    }

    private LibraryPolicyEntity defaults() {
        LibraryPolicyEntity policy = new LibraryPolicyEntity();
        policy.setPolicyId(1L);
        policy.setMaximumOpenItems(3);
        policy.setHomeLoanDays(10);
        policy.setRenewalDays(10);
        policy.setMaximumRenewalCount(1);
        policy.setWaitlistHoldHours(48);
        policy.setHomeLateFinePerDay(new BigDecimal("1000"));
        policy.setOnsiteLateFinePerDay(new BigDecimal("5000"));
        policy.setTechnicalProcessingFee(new BigDecimal("50000"));
        policy.setCompensationSettlementDays(30);
        return policy;
    }

    private LibraryPolicyHistoryEntity history(Long historyId, UUID actorId) {
        LibraryPolicyHistoryEntity history = new LibraryPolicyHistoryEntity();
        history.setHistoryId(historyId);
        history.setPolicyId(1L);
        history.setMaximumOpenItems(3);
        history.setHomeLoanDays(10);
        history.setRenewalDays(10);
        history.setMaximumRenewalCount(1);
        history.setWaitlistHoldHours(48);
        history.setHomeLateFinePerDay(new BigDecimal("1000"));
        history.setOnsiteLateFinePerDay(new BigDecimal("5000"));
        history.setTechnicalProcessingFee(new BigDecimal("50000"));
        history.setCompensationSettlementDays(30);
        history.setChangedBy(actorId);
        history.setChangedAt(LocalDateTime.of(2026, 10, 29, 8, 0));
        return history;
    }
}
