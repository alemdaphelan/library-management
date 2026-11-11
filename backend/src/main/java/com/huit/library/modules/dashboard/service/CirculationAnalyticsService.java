package com.huit.library.modules.dashboard.service;

import com.huit.library.modules.dashboard.dto.CirculationAnalytics.*;
import com.huit.library.modules.dashboard.repository.CirculationAnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CirculationAnalyticsService {
    public static final String TIMEZONE = "Asia/Ho_Chi_Minh";
    private final CirculationAnalyticsRepository repository;

    public CirculationAnalyticsService(CirculationAnalyticsRepository repository) {
        this.repository = repository;
    }

    public Report report(LocalDate from, LocalDate until, Period period) {
        var r = range(from, until, period);
        var at = now();
        return new Report(
                at,
                TIMEZONE,
                r,
                repository.totals(r, at),
                repository.buckets(r, at),
                repository.coverage(r, at));
    }

    public Report report(LocalDate date, LocalDate from, LocalDate until, Period period) {
        var r = resolveRange(date, from, until, period);
        return report(r.from(), r.until(), r.period());
    }

    public RankingPage ranking(LocalDate from, LocalDate until, Ranking mode, int page, int size) {
        page(page, size);
        var r = queryRange(from, until);
        var at = now();
        var ranking = mode == null ? Ranking.MOST_BORROWED : mode;
        return new RankingPage(
                at,
                TIMEZONE,
                r,
                ranking,
                page,
                size,
                repository.rankingCount(r, at, ranking),
                repository.ranking(r, at, ranking, page, size),
                repository.coverage(r, at));
    }

    public OverduePage overdue(int page, int size) {
        page(page, size);
        var at = now();
        return new OverduePage(
                at,
                TIMEZONE,
                page,
                size,
                repository.currentOverdueCount(at),
                repository.overdue(at, page, size));
    }

    public Range range(LocalDate from, LocalDate until, Period period) {
        var result = validatedRange(from, until, period);
        AnalyticsBuckets.bounds(
                result); // Limit only actual chart resolution, including hourly buckets.
        return result;
    }

    /** Paged drill-downs/rankings don't generate chart buckets. */
    public Range queryRange(LocalDate from, LocalDate until) {
        return validatedRange(from, until, Period.MONTH);
    }

    private Range validatedRange(LocalDate from, LocalDate until, Period period) {
        LocalDate today = now().toLocalDate();
        if (from == null) from = today.withDayOfYear(1);
        if (until == null) until = today.plusDays(1);
        if (from.getYear() < 1
                || until.getYear() > 9999
                || !from.isBefore(until)
                || from.plusYears(50).isBefore(until))
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Khoảng ngày đầu bao gồm/cuối loại trừ, tối đa 50 năm, năm 1–9999");
        return new Range(from, until, period == null ? Period.MONTH : period);
    }

    /** Reference date selects a whole calendar period; explicit ranges retain their clipping semantics. */
    public Range resolveRange(LocalDate date, LocalDate from, LocalDate until, Period period) {
        if (date == null) return range(from, until, period);
        if (from != null || until != null)
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Chọn ngày tham chiếu hoặc khoảng from/until");
        if (date.getYear() < 1 || date.getYear() > 9999)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Năm 1–9999");
        var p = period == null ? Period.MONTH : period;
        var start = AnalyticsBuckets.start(date, p);
        return range(start, AnalyticsBuckets.next(start, p), p);
    }

    private void page(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page không âm, size 1–100");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of(TIMEZONE));
    }
}
