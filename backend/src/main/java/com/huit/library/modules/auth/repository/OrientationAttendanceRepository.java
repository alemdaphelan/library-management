package com.huit.library.modules.auth.repository;

import com.huit.library.modules.auth.entity.OrientationAttendanceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrientationAttendanceRepository extends JpaRepository<OrientationAttendanceEntity, Long> {
}
