package com.huit.library.modules.circulation.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class CirculationScheduler {
    private static final Logger log = LoggerFactory.getLogger(CirculationScheduler.class);

    private final CirculationMaintenanceService maintenanceService;

    public CirculationScheduler(CirculationMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @Scheduled(cron = "${library.circulation.scheduler-cron:0 */5 * * * *}")
    public void maintainCirculation() {
        CirculationMaintenanceResult result = maintenanceService.processAt(LocalDateTime.now());
        if (result.expiredReservations() > 0
                || result.overdueTickets() > 0
                || result.dueSoonNotifications() > 0) {
            log.info(
                    "Circulation maintenance completed: expiredReservations={}, overdueTickets={}, dueSoonNotifications={}",
                    result.expiredReservations(), result.overdueTickets(), result.dueSoonNotifications());
        }
    }
}
