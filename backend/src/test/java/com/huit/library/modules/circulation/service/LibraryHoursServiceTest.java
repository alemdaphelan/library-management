package com.huit.library.modules.circulation.service;

import com.huit.library.modules.circulation.entity.HolidayCalendarEntity;
import com.huit.library.modules.circulation.repository.HolidayCalendarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(7, 29))))
                .isFalse();
        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(7, 30))))
                .isTrue();
        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(16, 59))))
                .isTrue();
        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(17, 0))))
                .isFalse();
    }

    @Test
    void closedHolidayNeverOpensCounter() {
        LocalDate date = LocalDate.of(2026, 11, 20);
        HolidayCalendarEntity holiday = new HolidayCalendarEntity();
        holiday.setDateId(date.toString());
        holiday.setIsClosed(true);
        when(holidayRepository.findById(date.toString())).thenReturn(Optional.of(holiday));

        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.NOON)))
                .isFalse();
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

        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(8, 59))))
                .isFalse();
        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(9, 0))))
                .isTrue();
        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(15, 29))))
                .isTrue();
        assertThat(service.isCounterOpen(LocalDateTime.of(date, java.time.LocalTime.of(15, 30))))
                .isFalse();
        assertThat(service.closingTime(date)).isEqualTo(java.time.LocalTime.of(15, 30));
    }

    @Test
    void requireCounterOpenUsesConfiguredHoursWithDeterministicClock() {
        Clock openClock = fixedClock("2026-11-03T08:00:00+07:00");
        service = new LibraryHoursService(holidayRepository, "07:30", "17:00", openClock);
        when(holidayRepository.findById("2026-11-03")).thenReturn(Optional.empty());

        assertThatCode(() -> service.requireCounterOpen("giao sách")).doesNotThrowAnyException();
    }

    @Test
    void requireCounterOpenReportsConfiguredHoursAfterClosingTime() {
        Clock closedClock = fixedClock("2026-11-03T17:00:00+07:00");
        service = new LibraryHoursService(holidayRepository, "07:30", "17:00", closedClock);
        when(holidayRepository.findById("2026-11-03")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.requireCounterOpen("nhận sách"))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                            assertThat(exception.getReason())
                                    .isEqualTo(
                                            "Quầy thư viện đã đóng cửa. Không thể nhận sách "
                                                    + "ngoài giờ phục vụ 07:30 - 17:00.");
                        });
    }

    private Clock fixedClock(String instantWithOffset) {
        return Clock.fixed(
                OffsetDateTime.parse(instantWithOffset).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
