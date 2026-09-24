package com.huit.library.modules.digital.repository;

import com.huit.library.modules.digital.entity.DigitalDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DigitalDocRepository extends JpaRepository<DigitalDocumentEntity, Long> {
}
