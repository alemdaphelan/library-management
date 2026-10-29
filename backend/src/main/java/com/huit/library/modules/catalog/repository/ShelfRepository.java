package com.huit.library.modules.catalog.repository;

import com.huit.library.modules.catalog.entity.ShelfEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ShelfRepository extends JpaRepository<ShelfEntity, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_READ)
    @org.springframework.data.jpa.repository.Query("""
            select shelf from ShelfEntity shelf
            join fetch shelf.bookshelf bookshelf join fetch bookshelf.floor floor
            where shelf.shelfId in :ids
            order by floor.floorId, bookshelf.bookshelfId, shelf.shelfId
            """)
    List<ShelfEntity> findImportLocationsForUpdate(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
    List<ShelfEntity> findByBookshelf_BookshelfId(Long bookshelfId);
}
