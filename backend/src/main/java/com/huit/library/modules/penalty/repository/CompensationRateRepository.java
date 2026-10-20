package com.huit.library.modules.penalty.repository;

import com.huit.library.modules.penalty.entity.CompensationRateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CompensationRateRepository extends JpaRepository<CompensationRateEntity, Long> {
    @Query("""
            select rate from CompensationRateEntity rate
            where upper(rate.materialType) = upper(:materialType)
              and upper(rate.languageCategory) = upper(:languageCategory)
              and rate.active = true
              and rate.validFrom <= :onDate
              and (rate.validUntil is null or rate.validUntil >= :onDate)
            order by rate.validFrom desc, rate.rateId desc
            """)
    List<CompensationRateEntity> findApplicable(
            @Param("materialType") String materialType,
            @Param("languageCategory") String languageCategory,
            @Param("onDate") LocalDate onDate);

    default Optional<CompensationRateEntity> findCurrent(
            String materialType, String languageCategory, LocalDate onDate) {
        return findApplicable(materialType, languageCategory, onDate).stream().findFirst();
    }
}
