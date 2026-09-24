package com.huit.library.modules.facilities.repository;

import com.huit.library.modules.facilities.entity.KioskEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface KioskRepository extends JpaRepository<KioskEntity, Long> {
}
