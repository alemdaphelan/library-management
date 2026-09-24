package com.huit.library.modules.facilities.repository;

import com.huit.library.modules.facilities.entity.RoomBookingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomBookingRepository extends JpaRepository<RoomBookingEntity, Long> {
}
