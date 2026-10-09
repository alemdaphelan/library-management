package com.huit.library.modules.catalog.repository;

import com.huit.library.modules.catalog.entity.ShelfEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ShelfRepository extends JpaRepository<ShelfEntity, Long> {
}
