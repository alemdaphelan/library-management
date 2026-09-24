package com.huit.library.modules.circulation.entity;

import com.huit.library.core.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "holiday_calendar")
public class HolidayCalendarEntity extends BaseEntity {
    @Id
    private String dateId;

    private String holidayName;
    private Boolean isClosed;
    private String overrideOpenTime;
    private String overrideCloseTime;
}
