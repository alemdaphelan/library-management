package com.huit.library.modules.circulation.repository;

import com.huit.library.modules.circulation.entity.LoanDetailEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanDetailRepository extends JpaRepository<LoanDetailEntity, Long> {
    List<LoanDetailEntity> findByTicketId(Long ticketId);
    Optional<LoanDetailEntity> findFirstByTicketId(Long ticketId);
    Optional<LoanDetailEntity> findFirstByBarcodeAndReturnDateIsNull(String barcode);
}
