package com.huit.library.modules.catalog.repository;

import com.huit.library.modules.catalog.entity.BookshelfEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookshelfRepository extends JpaRepository<BookshelfEntity, Long> {
    List<BookshelfEntity> findByFloor_FloorId(Long floorId);
}
