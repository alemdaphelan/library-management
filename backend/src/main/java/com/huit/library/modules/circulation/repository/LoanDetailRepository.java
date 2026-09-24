package com.huit.library.modules.circulation.repository;

import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LoanDetailRepository extends JpaRepository<LoanDetailEntity, Long> {
}
