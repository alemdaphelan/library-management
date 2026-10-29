package com.huit.library.modules.circulation.service;

public record CirculationMaintenanceResult(
        int expiredReservations,
        int overdueTickets,
        int dueSoonNotifications) {
}
