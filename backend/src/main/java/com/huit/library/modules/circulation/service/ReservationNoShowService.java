package com.huit.library.modules.circulation.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ReservationNoShowService {
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final int threshold;
    private final int lockMonths;
    private final int academicYearStartMonth;
    private final int academicYearStartDay;

    public ReservationNoShowService(
            UserRepository userRepository,
            NotificationService notificationService,
            @Value("${library.circulation.reservation-no-show-threshold:3}") int threshold,
            @Value("${library.circulation.reservation-no-show-lock-months:3}") int lockMonths,
            @Value("${library.circulation.academic-year-start-month:8}") int academicYearStartMonth,
            @Value("${library.circulation.academic-year-start-day:1}") int academicYearStartDay) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.threshold = threshold;
        this.lockMonths = lockMonths;
        this.academicYearStartMonth = academicYearStartMonth;
        this.academicYearStartDay = academicYearStartDay;
    }

    @Transactional
    public int record(UUID userId, LocalDateTime occurredAt, String referenceType, String referenceId) {
        UserEntity user = userRepository.findByIdForUpdate(userId).orElse(null);
        if (user == null) {
            return 0;
        }
        String academicYear = academicYear(occurredAt.toLocalDate());
        int current = academicYear.equals(user.getReservationViolationAcademicYear())
                && user.getReservationNoShowCount() != null
                ? user.getReservationNoShowCount() : 0;
        int updated = current + 1;
        user.setReservationViolationAcademicYear(academicYear);
        user.setReservationNoShowCount(updated);

        if (updated >= threshold) {
            LocalDateTime proposedLock = occurredAt.plusMonths(lockMonths);
            if (user.getCirculationLockedUntil() == null
                    || user.getCirculationLockedUntil().isBefore(proposedLock)) {
                user.setCirculationLockedUntil(proposedLock);
            }
            user.setCirculationLockReason(
                    "Không đến nhận tài liệu " + updated + " lần trong năm học " + academicYear);
            notificationService.createOnce(
                    userId, "CIRCULATION_LOCKED",
                    "Quyền mượn, đặt giữ và gia hạn bị khóa đến "
                            + user.getCirculationLockedUntil().toLocalDate()
                            + " do không đến nhận tài liệu " + updated + " lần.",
                    "USER", userId.toString(),
                    "RESERVATION_NO_SHOW_LOCK:" + academicYear, occurredAt);
        } else {
            notificationService.createOnce(
                    userId, "RESERVATION_NO_SHOW",
                    "Bạn đã không đến nhận tài liệu " + updated + "/" + threshold
                            + " lần trong năm học. Đủ " + threshold
                            + " lần sẽ bị khóa quyền mượn trong " + lockMonths + " tháng.",
                    referenceType, referenceId,
                    "RESERVATION_NO_SHOW:" + referenceType + ":" + referenceId, occurredAt);
        }
        userRepository.save(user);
        return updated;
    }

    String academicYear(LocalDate date) {
        LocalDate startThisYear = LocalDate.of(
                date.getYear(), academicYearStartMonth, academicYearStartDay);
        int startYear = date.isBefore(startThisYear) ? date.getYear() - 1 : date.getYear();
        return startYear + "-" + (startYear + 1);
    }
}
