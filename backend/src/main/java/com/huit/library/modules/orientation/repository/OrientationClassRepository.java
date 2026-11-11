package com.huit.library.modules.orientation.repository;

import com.huit.library.modules.orientation.entity.OrientationClassEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrientationClassRepository extends JpaRepository<OrientationClassEntity, Long> {
    List<OrientationClassEntity> findAllByOrderByStartAtAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OrientationClassEntity c where c.classId = :id")
    Optional<OrientationClassEntity> findByIdForUpdate(@Param("id") Long id);

    boolean existsByFacilitatorIdAndStartAtLessThanAndEndAtGreaterThan(
            UUID facilitatorId, LocalDateTime endAt, LocalDateTime startAt);

    boolean existsByFacilitatorIdAndClassIdNotAndStartAtLessThanAndEndAtGreaterThan(
            UUID facilitatorId, Long classId, LocalDateTime endAt, LocalDateTime startAt);
}
