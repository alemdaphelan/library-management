package com.huit.library.modules.orientation.repository;

import com.huit.library.modules.orientation.entity.OrientationEnrollmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrientationEnrollmentRepository extends JpaRepository<OrientationEnrollmentEntity, Long> {
    List<OrientationEnrollmentEntity> findByClassIdOrderByEnrollmentIdAsc(Long classId);
    Optional<OrientationEnrollmentEntity> findByClassIdAndUserId(Long classId, UUID userId);
    Optional<OrientationEnrollmentEntity> findByEnrollmentIdAndClassId(Long enrollmentId, Long classId);
    long countByClassId(Long classId);
    boolean existsByUserIdAndAttendanceStatus(UUID userId, String attendanceStatus);
}
