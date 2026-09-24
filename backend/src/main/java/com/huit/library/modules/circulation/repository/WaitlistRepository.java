package com.huit.library.modules.circulation.repository;

import com.huit.library.modules.circulation.entity.WaitlistEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WaitlistRepository extends JpaRepository<WaitlistEntity, Long> {
}
