package com.huit.library.modules.circulation.service;

import com.huit.library.modules.circulation.entity.HolidayCalendarEntity;
import com.huit.library.modules.circulation.repository.HolidayCalendarRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
public class LibraryHoursService {
    private final HolidayCalendarRepository holidayRepository;
    private final LocalTime defaultOpenTime;
    private final LocalTime defaultCloseTime;

    public LibraryHoursService(
            HolidayCalendarRepository holidayRepository,
            @Value("${library.circulation.default-open-time:07:30}") String defaultOpenTime,
            @Value("${library.circulation.default-close-time:17:00}") String defaultCloseTime) {
        this.holidayRepository = holidayRepository;
        this.defaultOpenTime = LocalTime.parse(defaultOpenTime);
        this.defaultCloseTime = LocalTime.parse(defaultCloseTime);
    }

    public boolean isCounterOpen(LocalDateTime at) {
        LocalDate date = at.toLocalDate();
        if (isClosed(date)) return false;
        LocalTime time = at.toLocalTime();
        return !time.isBefore(openingTime(date)) && time.isBefore(closingTime(date));
    }

    public void requireCounterOpen(String operation) {
        LocalDateTime now = LocalDateTime.now();
        if (!isCounterOpen(now)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Quầy thư viện đã đóng cửa. Không thể " + operation
                            + " ngoài giờ phục vụ " + openingTime(now.toLocalDate())
                            + " - " + closingTime(now.toLocalDate()) + ".");
        }
    }

    public LocalTime closingTime(LocalDate date) {
        return holidayRepository.findById(date.toString())
                .map(HolidayCalendarEntity::getOverrideCloseTime)
                .filter(value -> value != null && !value.isBlank())
                .map(LocalTime::parse)
                .orElse(defaultCloseTime);
    }

    private LocalTime openingTime(LocalDate date) {
        return holidayRepository.findById(date.toString())
                .map(HolidayCalendarEntity::getOverrideOpenTime)
                .filter(value -> value != null && !value.isBlank())
                .map(LocalTime::parse)
                .orElse(defaultOpenTime);
    }

    private boolean isClosed(LocalDate date) {
        return holidayRepository.findById(date.toString())
                .map(day -> Boolean.TRUE.equals(day.getIsClosed()))
                .orElse(false);
    }
}
