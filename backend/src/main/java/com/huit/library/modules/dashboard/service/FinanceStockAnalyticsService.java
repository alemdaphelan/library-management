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
        for(var b:bounds(range))buckets.add(new MoneyBucket(b.start(),b.until(),grouped.getOrDefault(b.key(),ZERO_MONEY)));
        return new FinanceReport(at,CirculationAnalyticsService.TIMEZONE,range,finance.totals(range,at),buckets,
                finance.fees(range,at),finance.currentDebt(),finance.coverage(),false);
    }
    public EventPage events(LocalDate from,LocalDate until,EventKind kind,int page,int size) {
        page(page,size);var range=ranges.range(from,until,Period.MONTH);var at=now();
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
        for(var b:bounds(range))buckets.add(new ImportBucket(b.start(),b.until(),grouped.getOrDefault(b.key(),ZERO_IMPORTS),null,null));
        return new StockReport(at,CirculationAnalyticsService.TIMEZONE,range,imports.totals(range,at),buckets,imports.coverage(),
                new DisposalAvailability("WAITING_ADMIN_WORKFLOW",
                        "Chưa có hợp đồng/sự kiện thực thi thanh lý hoặc hủy của Admin; không tính đề xuất hay trạng thái copy thành kết quả.",null,null));
    }
    public ReceiptPage receipts(LocalDate from,LocalDate until,int page,int size) {
        page(page,size);var range=ranges.range(from,until,Period.MONTH);var at=now();
        return new ReceiptPage(at,CirculationAnalyticsService.TIMEZONE,range,page,size,
                imports.receiptCount(range,at),imports.receipts(range,at,page,size));
    }
    private List<Bounds> bounds(Range r) {
        List<Bounds> result=new ArrayList<>();
        LocalDate start=switch(r.period()) {
            case MONTH -> r.from().withDayOfMonth(1);
            case QUARTER -> LocalDate.of(r.from().getYear(),((r.from().getMonthValue()-1)/3)*3+1,1);
            case YEAR -> r.from().withDayOfYear(1);
        };
        int months=switch(r.period()){case MONTH->1;case QUARTER->3;case YEAR->12;};
        while(start.isBefore(r.until())) {
            LocalDate end=start.plusMonths(months);
            result.add(new Bounds(start,start.isBefore(r.from())?r.from():start,end.isAfter(r.until())?r.until():end));
            start=end;
        }
        return result;
    }
    private record Bounds(LocalDate key,LocalDate start,LocalDate until) {}
    private void page(int page,int size) {
        if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"page không âm, size 1–100");
    }
    private LocalDateTime now(){return LocalDateTime.now(ZoneId.of(CirculationAnalyticsService.TIMEZONE));}
}
