package com.huit.library.modules.inventory.repository;

import com.huit.library.modules.inventory.entity.ShiftHandoverEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShiftHandoverRepository extends JpaRepository<ShiftHandoverEntity, Long> {
}
