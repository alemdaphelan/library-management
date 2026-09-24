package com.huit.library.modules.circulation.repository;

import com.huit.library.modules.circulation.entity.HolidayCalendarEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendarEntity, String> {
}
