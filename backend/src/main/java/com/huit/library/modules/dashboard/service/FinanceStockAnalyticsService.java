package com.huit.library.modules.dashboard.service;

import com.huit.library.modules.dashboard.dto.CirculationAnalytics.*;
import com.huit.library.modules.dashboard.dto.CirculationAnalytics.Period;
import com.huit.library.modules.dashboard.dto.FinanceStockAnalytics.*;
import com.huit.library.modules.dashboard.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class FinanceStockAnalyticsService {
    private final FinanceAnalyticsRepository finance;
    private final ImportAnalyticsRepository imports;
    private final CirculationAnalyticsService ranges;
    private static final Money ZERO_MONEY=new Money(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
            BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,
            BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO);
    private static final Imports ZERO_IMPORTS=new Imports(0,0,0,0);
    public FinanceStockAnalyticsService(FinanceAnalyticsRepository finance,ImportAnalyticsRepository imports,CirculationAnalyticsService ranges) {
        this.finance=finance;this.imports=imports;this.ranges=ranges;
    }
    public FinanceReport finance(LocalDate from,LocalDate until,Period period) {
        var range=ranges.range(from,until,period);var at=now();var grouped=finance.buckets(range,at);
        List<MoneyBucket> buckets=new ArrayList<>();
        for(var b:AnalyticsBuckets.bounds(range))buckets.add(new MoneyBucket(b.start(),b.until(),grouped.getOrDefault(b.key(),ZERO_MONEY)));
        return new FinanceReport(at,CirculationAnalyticsService.TIMEZONE,range,finance.totals(range,at),buckets,
                finance.fees(range,at),finance.currentDebt(),finance.coverage(),false);
    }
    public FinanceReport finance(LocalDate date,LocalDate from,LocalDate until,Period period) {
        var r=ranges.resolveRange(date,from,until,period);
        return finance(r.from(),r.until(),r.period());
    }
    public EventPage events(LocalDate from,LocalDate until,EventKind kind,int page,int size) {
        page(page,size);var range=ranges.queryRange(from,until);var at=now();
        return new EventPage(at,CirculationAnalyticsService.TIMEZONE,range,kind,page,size,
                finance.eventCount(range,at,kind),finance.events(range,at,kind,page,size));
    }
    public AllocationPage allocations(long id,int page,int size) {
        page(page,size);
        if(!finance.paymentExists(id))throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Không tìm thấy giao dịch PAYMENT");
        return new AllocationPage(now(),id,page,size,finance.allocationCount(id),finance.allocations(id,page,size));
    }
    public StockReport stock(LocalDate from,LocalDate until,Period period) {
        var range=ranges.range(from,until,period);var at=now();var grouped=imports.buckets(range,at);
        List<ImportBucket> buckets=new ArrayList<>();
        for(var b:AnalyticsBuckets.bounds(range))buckets.add(new ImportBucket(b.start(),b.until(),grouped.getOrDefault(b.key(),ZERO_IMPORTS),null,null));
        return new StockReport(at,CirculationAnalyticsService.TIMEZONE,range,imports.totals(range,at),buckets,imports.coverage(),
                new DisposalAvailability("WAITING_ADMIN_WORKFLOW",
                        "Chưa có hợp đồng/sự kiện thực thi thanh lý hoặc hủy của Admin; không tính đề xuất hay trạng thái copy thành kết quả.",null,null));
    }
    public StockReport stock(LocalDate date,LocalDate from,LocalDate until,Period period) {
        var r=ranges.resolveRange(date,from,until,period);
        return stock(r.from(),r.until(),r.period());
    }
    public ReceiptPage receipts(LocalDate from,LocalDate until,int page,int size) {
        page(page,size);var range=ranges.queryRange(from,until);var at=now();
        return new ReceiptPage(at,CirculationAnalyticsService.TIMEZONE,range,page,size,
                imports.receiptCount(range,at),imports.receipts(range,at,page,size));
    }
    private void page(int page,int size) {
        if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"page không âm, size 1–100");
    }
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of(CirculationAnalyticsService.TIMEZONE));}
}
