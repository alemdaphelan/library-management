package com.huit.library.modules.dashboard.dto;
import java.time.*;
import java.util.*;

public final class CirculationAnalytics {
    private CirculationAnalytics() {}
    public enum Period { MONTH, QUARTER, YEAR }
    public enum Ranking { MOST_BORROWED, LEAST_BORROWED, NEVER_RECORDED, NO_RECORDED_IN_PERIOD, CURRENTLY_BORROWED }
    public record Range(LocalDate from, LocalDate until, Period period) {}
    public record Counts(long checkoutItems, long physicalReturnItems, long checkoutTickets, long physicalReturnTickets) {}
    public record Coverage(long checkoutRecordsWithoutTime, long normalReturnsWithoutTime, long ambiguousIncidentReturns,
            long periodCheckoutsWithoutCurrentCatalog, boolean complete) {}
    public record Bucket(LocalDate start, LocalDate until, Counts counts) {}
    public record Report(LocalDateTime snapshotAt, String timezone, Range range, Counts totals, List<Bucket> buckets, Coverage coverage) {}
    public record RankedBook(long bookId, String title, String author, long recordedInPeriod, long recordedLifetime,
            long currentlyBorrowed, long copyCount, long position, String bookUrl) {}
    public record RankingPage(LocalDateTime snapshotAt, String timezone, Range range, Ranking ranking, int page, int size,
            long totalBooks, List<RankedBook> books, Coverage coverage) {}
    public record OverdueItem(UUID userId, String mssv, String fullName, long ticketId, long loanDetailId, String barcode,
            Long bookId, String title, LocalDateTime dueAt, String ticketUrl, String studentReportApi) {}
    public record OverduePage(LocalDateTime snapshotAt, String timezone, int page, int size, long totalItems, List<OverdueItem> items) {}
}
