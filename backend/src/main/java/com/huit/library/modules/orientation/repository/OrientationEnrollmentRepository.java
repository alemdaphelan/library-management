package com.huit.library.modules.orientation.repository;

import com.huit.library.modules.orientation.entity.OrientationEnrollmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrientationEnrollmentRepository extends JpaRepository<OrientationEnrollmentEntity, Long> {
    interface StudentCandidate {
        UUID getUserId();
        String getFullName();
        String getMssv();
        String getEmail();
    }

    @Query("""
            select u.userId as userId, u.fullName as fullName, u.mssv as mssv, u.email as email
            from UserEntity u
            where u.userType = 'STUDENT' and (u.cardStatus is null or trim(u.cardStatus) = '')
              and not exists (select 1 from OrientationEnrollmentEntity e where e.userId = u.userId
                              and (e.classId = :classId or e.attendanceStatus = 'ABSENT'))
            order by u.fullName
            """)
    List<StudentCandidate> findAvailableStudents(@Param("classId") Long classId);

    List<OrientationEnrollmentEntity> findByClassIdOrderByEnrollmentIdAsc(Long classId);
    Optional<OrientationEnrollmentEntity> findByClassIdAndUserId(Long classId, UUID userId);
    Optional<OrientationEnrollmentEntity> findByEnrollmentIdAndClassId(Long enrollmentId, Long classId);
    long countByClassId(Long classId);
    boolean existsByUserIdAndAttendanceStatus(UUID userId, String attendanceStatus);
}
