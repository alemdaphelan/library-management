package com.huit.library.modules.policy.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.policy.dto.LibraryPolicyHistoryResponseDTO;
import com.huit.library.modules.policy.dto.LibraryPolicyRequestDTO;
import com.huit.library.modules.policy.dto.LibraryPolicyPublicResponseDTO;
import com.huit.library.modules.policy.dto.LibraryPolicyResponseDTO;
import com.huit.library.modules.policy.entity.LibraryPolicyEntity;
import com.huit.library.modules.policy.entity.LibraryPolicyHistoryEntity;
import com.huit.library.modules.policy.repository.LibraryPolicyHistoryRepository;
import com.huit.library.modules.policy.repository.LibraryPolicyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LibraryPolicyService {
    private static final long POLICY_ID = 1L;
    private final LibraryPolicyRepository repository;
    private final LibraryPolicyHistoryRepository historyRepository;
    private final UserRepository userRepository;

    public LibraryPolicyService(
            LibraryPolicyRepository repository,
            LibraryPolicyHistoryRepository historyRepository,
            UserRepository userRepository) {
        this.repository = repository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public LibraryPolicyResponseDTO getPolicy() {
        return toResponse(current());
    }

    @Transactional(readOnly = true)
    public LibraryPolicyPublicResponseDTO getPublicPolicy() {
        LibraryPolicyEntity policy = current();
        return LibraryPolicyPublicResponseDTO.builder()
                .maximumOpenItems(policy.getMaximumOpenItems())
                .homeLoanDays(policy.getHomeLoanDays())
                .renewalDays(policy.getRenewalDays())
                .maximumRenewalCount(policy.getMaximumRenewalCount())
                .waitlistHoldHours(policy.getWaitlistHoldHours())
                .homeLateFinePerDay(policy.getHomeLateFinePerDay())
                .onsiteLateFinePerDay(policy.getOnsiteLateFinePerDay())
                .technicalProcessingFee(policy.getTechnicalProcessingFee())
                .compensationSettlementDays(policy.getCompensationSettlementDays())
                .build();
    }

    @Transactional(readOnly = true)
    public List<LibraryPolicyHistoryResponseDTO> getHistory() {
        List<LibraryPolicyHistoryEntity> history = historyRepository.findAllByOrderByChangedAtDescHistoryIdDesc();
        List<UUID> actorIds = history.stream()
                .map(LibraryPolicyHistoryEntity::getChangedBy)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, UserEntity> actors = actorIds.isEmpty()
                ? Collections.emptyMap()
                : userRepository.findAllById(actorIds).stream()
                        .collect(Collectors.toMap(UserEntity::getUserId, Function.identity()));
        return history.stream().map(item -> toHistoryResponse(item, actors.get(item.getChangedBy()))).toList();
    }

    @Transactional
    public LibraryPolicyResponseDTO updatePolicy(LibraryPolicyRequestDTO request, UUID actorId) {
        LibraryPolicyEntity policy = current();
        policy.setMaximumOpenItems(request.getMaximumOpenItems());
        policy.setHomeLoanDays(request.getHomeLoanDays());
        policy.setRenewalDays(request.getRenewalDays());
        policy.setMaximumRenewalCount(request.getMaximumRenewalCount());
        policy.setWaitlistHoldHours(request.getWaitlistHoldHours());
        policy.setHomeLateFinePerDay(request.getHomeLateFinePerDay());
        policy.setOnsiteLateFinePerDay(request.getOnsiteLateFinePerDay());
        policy.setTechnicalProcessingFee(request.getTechnicalProcessingFee());
        policy.setCompensationSettlementDays(request.getCompensationSettlementDays());
        policy.setUpdatedBy(actorId);
        LibraryPolicyEntity saved = repository.save(policy);
        historyRepository.save(snapshot(saved, actorId));
        return toResponse(saved);
    }

    public int maximumOpenItems() { return current().getMaximumOpenItems(); }
    public int homeLoanDays() { return current().getHomeLoanDays(); }
    public int renewalDays() { return current().getRenewalDays(); }
    public int maximumRenewalCount() { return current().getMaximumRenewalCount(); }
    public int waitlistHoldHours() { return current().getWaitlistHoldHours(); }
    public BigDecimal homeLateFinePerDay() { return current().getHomeLateFinePerDay(); }
    public BigDecimal onsiteLateFinePerDay() { return current().getOnsiteLateFinePerDay(); }
    public BigDecimal technicalProcessingFee() { return current().getTechnicalProcessingFee(); }
    public int compensationSettlementDays() { return current().getCompensationSettlementDays(); }

    private LibraryPolicyEntity current() {
        return repository.findById(POLICY_ID).orElseGet(this::defaults);
    }

    private LibraryPolicyEntity defaults() {
        LibraryPolicyEntity policy = new LibraryPolicyEntity();
        policy.setPolicyId(POLICY_ID);
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

    private LibraryPolicyResponseDTO toResponse(LibraryPolicyEntity policy) {
        return LibraryPolicyResponseDTO.builder()
                .maximumOpenItems(policy.getMaximumOpenItems())
                .homeLoanDays(policy.getHomeLoanDays())
                .renewalDays(policy.getRenewalDays())
                .maximumRenewalCount(policy.getMaximumRenewalCount())
                .waitlistHoldHours(policy.getWaitlistHoldHours())
                .homeLateFinePerDay(policy.getHomeLateFinePerDay())
                .onsiteLateFinePerDay(policy.getOnsiteLateFinePerDay())
                .technicalProcessingFee(policy.getTechnicalProcessingFee())
                .compensationSettlementDays(policy.getCompensationSettlementDays())
                .updatedBy(policy.getUpdatedBy())
                .updatedAt(policy.getUpdatedAt())
                .build();
    }

    private LibraryPolicyHistoryEntity snapshot(LibraryPolicyEntity policy, UUID actorId) {
        LibraryPolicyHistoryEntity history = new LibraryPolicyHistoryEntity();
        history.setPolicyId(policy.getPolicyId());
        history.setMaximumOpenItems(policy.getMaximumOpenItems());
        history.setHomeLoanDays(policy.getHomeLoanDays());
        history.setRenewalDays(policy.getRenewalDays());
        history.setMaximumRenewalCount(policy.getMaximumRenewalCount());
        history.setWaitlistHoldHours(policy.getWaitlistHoldHours());
        history.setHomeLateFinePerDay(policy.getHomeLateFinePerDay());
        history.setOnsiteLateFinePerDay(policy.getOnsiteLateFinePerDay());
        history.setTechnicalProcessingFee(policy.getTechnicalProcessingFee());
        history.setCompensationSettlementDays(policy.getCompensationSettlementDays());
        history.setChangedBy(actorId);
        return history;
    }

    private LibraryPolicyHistoryResponseDTO toHistoryResponse(
            LibraryPolicyHistoryEntity history,
            UserEntity actor) {
        return LibraryPolicyHistoryResponseDTO.builder()
                .historyId(history.getHistoryId())
                .maximumOpenItems(history.getMaximumOpenItems())
                .homeLoanDays(history.getHomeLoanDays())
                .renewalDays(history.getRenewalDays())
                .maximumRenewalCount(history.getMaximumRenewalCount())
                .waitlistHoldHours(history.getWaitlistHoldHours())
                .homeLateFinePerDay(history.getHomeLateFinePerDay())
                .onsiteLateFinePerDay(history.getOnsiteLateFinePerDay())
                .technicalProcessingFee(history.getTechnicalProcessingFee())
                .compensationSettlementDays(history.getCompensationSettlementDays())
                .changedBy(history.getChangedBy())
                .changedByName(actor == null
                        ? (history.getChangedBy() == null ? "Hệ thống" : "Quản trị viên")
                        : actor.getFullName())
                .changedByEmail(actor == null ? null : actor.getEmail())
                .changedAt(history.getChangedAt())
                .build();
    }
}
