package com.huit.library.modules.dashboard.dto;

import java.time.LocalDate;

public record WeeklyStatDTO(LocalDate date, String day, long count, int percentage) {
}
