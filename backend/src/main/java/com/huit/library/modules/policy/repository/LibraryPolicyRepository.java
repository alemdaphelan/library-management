package com.huit.library.modules.policy.repository;

import com.huit.library.modules.policy.entity.LibraryPolicyEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LibraryPolicyRepository extends JpaRepository<LibraryPolicyEntity, Long> {
}
