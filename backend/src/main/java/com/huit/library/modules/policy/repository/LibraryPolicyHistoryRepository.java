package com.huit.library.modules.policy.repository;

import com.huit.library.modules.policy.entity.LibraryPolicyHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LibraryPolicyHistoryRepository extends JpaRepository<LibraryPolicyHistoryEntity, Long> {
    List<LibraryPolicyHistoryEntity> findAllByOrderByChangedAtDescHistoryIdDesc();
}
