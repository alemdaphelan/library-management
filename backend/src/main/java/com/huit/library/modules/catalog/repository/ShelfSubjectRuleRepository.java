package com.huit.library.modules.catalog.repository;

import com.huit.library.modules.catalog.entity.ShelfSubjectRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShelfSubjectRuleRepository extends JpaRepository<ShelfSubjectRuleEntity, Long> {
    List<ShelfSubjectRuleEntity> findByShelf_ShelfIdAndActiveTrueOrderBySubject_NameAsc(Long shelfId);

    void deleteByShelf_ShelfId(Long shelfId);
}
