package com.huit.library.modules.facilities.repository;

import com.huit.library.modules.facilities.entity.RoomBookingMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomBookingMemberRepository extends JpaRepository<RoomBookingMemberEntity, Long> {
}
