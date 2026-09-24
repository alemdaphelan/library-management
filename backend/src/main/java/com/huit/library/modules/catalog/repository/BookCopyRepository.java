package com.huit.library.modules.catalog.repository;

import com.huit.library.modules.catalog.entity.BookCopyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookCopyRepository extends JpaRepository<BookCopyEntity, String> {
}
