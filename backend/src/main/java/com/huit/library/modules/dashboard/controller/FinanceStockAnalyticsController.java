package com.huit.library.modules.dashboard.controller;

import com.huit.library.modules.dashboard.dto.CirculationAnalytics.Period;
import com.huit.library.modules.dashboard.dto.FinanceStockAnalytics.EventKind;
import com.huit.library.modules.dashboard.service.FinanceStockAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

@RestController
@RequestMapping("/dashboard/analytics")
@PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
public class FinanceStockAnalyticsController {
    private final FinanceStockAnalyticsService service;
    public FinanceStockAnalyticsController(FinanceStockAnalyticsService service){this.service=service;}

    @GetMapping("/finance")
    public ResponseEntity<?> finance(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,
            @RequestParam(defaultValue="MONTH") Period period){return ok(service.finance(from,until,period));}
    @GetMapping("/finance/entries")
    public ResponseEntity<?> events(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,
            @RequestParam(required=false) EventKind kind,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return ok(service.events(from,until,kind,page,size));}
    @GetMapping("/finance/payments/{id}/allocations")
    public ResponseEntity<?> allocations(@PathVariable long id,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="25") int size){return ok(service.allocations(id,page,size));}
    @GetMapping("/stock-movements")
    public ResponseEntity<?> stock(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,
            @RequestParam(defaultValue="MONTH") Period period){return ok(service.stock(from,until,period));}
    @GetMapping("/stock-movements/receipts")
    public ResponseEntity<?> receipts(
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return ok(service.receipts(from,until,page,size));}
    private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
