package com.huit.library.modules.dashboard.service;
import com.huit.library.modules.dashboard.dto.CirculationAnalytics.*;
import com.huit.library.modules.dashboard.dto.CirculationAnalytics.Period;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
public final class AnalyticsBuckets {
    private AnalyticsBuckets(){}
    public static LocalDate start(LocalDate date,Period period){return switch(period){
        case DAY->date;case WEEK->date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        case MONTH->date.withDayOfMonth(1);case QUARTER->LocalDate.of(date.getYear(),((date.getMonthValue()-1)/3)*3+1,1);case YEAR->date.withDayOfYear(1);
    };}
    public static LocalDate next(LocalDate date,Period period){return switch(period){
        case DAY->date.plusDays(1);case WEEK->date.plusWeeks(1);case MONTH->date.plusMonths(1);case QUARTER->date.plusMonths(3);case YEAR->date.plusYears(1);
    };}
    public static final int MAX_BUCKETS = 1000;
    public static BucketUnit unit(Period period) {
        return period.bucketUnit();
    }
    public static String sqlUnit(Period period) {
        return unit(period).name().toLowerCase(java.util.Locale.ROOT);
    }
    public static String sqlStep(Period period) {
        return switch (unit(period)) {
            case HOUR -> "1 hour";
            case DAY -> "1 day";
            case MONTH -> "1 month";
            case QUARTER -> "3 months";
        };
    }
    public record Bounds(LocalDateTime key, LocalDateTime start, LocalDateTime until) {}
    public static List<Bounds> bounds(Range range) {
        var from = range.from().atStartOfDay();
        var until = range.until().atStartOfDay();
        var unit = unit(range.period());
        var key = switch (unit) {
            case HOUR, DAY -> from;
            case MONTH -> range.from().withDayOfMonth(1).atStartOfDay();
            case QUARTER -> start(range.from(), Period.QUARTER).atStartOfDay();
        };
        List<Bounds> result = new ArrayList<>();
        while (key.isBefore(until)) {
            if (result.size() == MAX_BUCKETS) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tối đa 1000 khoảng thống kê; chọn kỳ lớn hơn hoặc thu hẹp ngày");
            var end = switch (unit) {
                case HOUR -> key.plusHours(1);
                case DAY -> key.plusDays(1);
                case MONTH -> key.plusMonths(1);
                case QUARTER -> key.plusMonths(3);
            };
            result.add(new Bounds(key, key.isBefore(from) ? from : key, end.isAfter(until) ? until : end));
            key = end;
        }
        return result;
    }
}
