package com.huit.library.modules.digital.repository;

import com.huit.library.modules.digital.entity.DigitalDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DigitalDocumentRepository extends JpaRepository<DigitalDocumentEntity, Long> {
    List<DigitalDocumentEntity> findAllByOrderByCreatedAtDesc();
}
