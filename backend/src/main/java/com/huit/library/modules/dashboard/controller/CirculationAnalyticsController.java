package com.huit.library.modules.dashboard.controller;
import com.huit.library.modules.dashboard.dto.CirculationAnalytics.*;
import com.huit.library.modules.dashboard.service.CirculationAnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
@RestController @RequestMapping("/dashboard/analytics") @PreAuthorize("hasAnyRole('ADMIN','LIBRARIAN')")
public class CirculationAnalyticsController {
    private final CirculationAnalyticsService service;public CirculationAnalyticsController(CirculationAnalyticsService service){this.service=service;}
    @GetMapping("/circulation") public ResponseEntity<?> report(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,@RequestParam(defaultValue="MONTH") Period period){return ok(service.report(from,until,period));}
    @GetMapping("/rankings") public ResponseEntity<?> ranking(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate until,@RequestParam(defaultValue="MOST_BORROWED") Ranking ranking,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size){return ok(service.ranking(from,until,ranking,page,size));}
    @GetMapping("/overdue") public ResponseEntity<?> overdue(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return ok(service.overdue(page,size));}
    private ResponseEntity<?> ok(Object body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
