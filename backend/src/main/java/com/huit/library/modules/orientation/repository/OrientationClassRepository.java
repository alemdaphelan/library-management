package com.huit.library.modules.orientation.repository;

import com.huit.library.modules.orientation.entity.OrientationClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface OrientationClassRepository extends JpaRepository<OrientationClassEntity, Long> {
    List<OrientationClassEntity> findAllByOrderByStartAtAsc();
    boolean existsByFacilitatorIdAndStartAtLessThanAndEndAtGreaterThan(
            UUID facilitatorId, LocalDateTime endAt, LocalDateTime startAt);
    boolean existsByFacilitatorIdAndClassIdNotAndStartAtLessThanAndEndAtGreaterThan(
            UUID facilitatorId, Long classId, LocalDateTime endAt, LocalDateTime startAt);
}
