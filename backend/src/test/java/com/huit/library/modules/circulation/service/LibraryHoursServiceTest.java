package com.huit.library.modules.circulation.service;

import com.huit.library.modules.circulation.entity.HolidayCalendarEntity;
import com.huit.library.modules.circulation.repository.HolidayCalendarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LibraryHoursServiceTest {
    private HolidayCalendarRepository holidayRepository;
    private LibraryHoursService service;

    @BeforeEach
    void setUp() {
        holidayRepository = mock(HolidayCalendarRepository.class);
        service = new LibraryHoursService(holidayRepository, "07:30", "17:00");
    }

    @Test
    void counterIsOpenOnlyInsideConfiguredServiceHours() {
        LocalDate date = LocalDate.of(2026, 10, 28);
        when(holidayRepository.findById(date.toString())).thenReturn(Optional.empty());

        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(7, 29)))).isFalse();
        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(7, 30)))).isTrue();
        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(16, 59)))).isTrue();
        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(17, 0)))).isFalse();
    }

    @Test
    void closedHolidayNeverOpensCounter() {
        LocalDate date = LocalDate.of(2026, 11, 20);
        HolidayCalendarEntity holiday = new HolidayCalendarEntity();
        holiday.setDateId(date.toString());
        holiday.setIsClosed(true);
        when(holidayRepository.findById(date.toString())).thenReturn(Optional.of(holiday));

        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.NOON))).isFalse();
    }

    @Test
    void holidayOverridesOpeningAndClosingTimes() {
        LocalDate date = LocalDate.of(2026, 12, 1);
        HolidayCalendarEntity holiday = new HolidayCalendarEntity();
        holiday.setDateId(date.toString());
        holiday.setIsClosed(false);
        holiday.setOverrideOpenTime("09:00");
        holiday.setOverrideCloseTime("15:30");
        when(holidayRepository.findById(date.toString())).thenReturn(Optional.of(holiday));

        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(8, 59)))).isFalse();
        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(9, 0)))).isTrue();
        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(15, 29)))).isTrue();
        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(15, 30)))).isFalse();
        assertThat(service.closingTime(date)).isEqualTo(java.time.LocalTime.of(15, 30));
    }
}
