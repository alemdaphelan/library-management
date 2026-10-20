package com.huit.library.modules.penalty.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.circulation.entity.LoanTicketEntity;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.penalty.entity.OverdueViolationEntity;
import com.huit.library.modules.penalty.repository.OverdueViolationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class OverdueViolationService {
    private final OverdueViolationRepository violationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final int academicYearStartMonth;
    private final int academicYearStartDay;

    public OverdueViolationService(
            OverdueViolationRepository violationRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            @Value("${library.circulation.academic-year-start-month:8}") int academicYearStartMonth,
            @Value("${library.circulation.academic-year-start-day:1}") int academicYearStartDay) {
        this.violationRepository = violationRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.academicYearStartMonth = academicYearStartMonth;
        this.academicYearStartDay = academicYearStartDay;
    }

    @Transactional
    public boolean recordFirstOverdue(LoanTicketEntity ticket, LocalDateTime occurredAt) {
        String academicYear = academicYear(occurredAt.toLocalDate());
        UserEntity user = userRepository.findByIdForUpdate(ticket.getUserId()).orElse(null);
        if (user == null || violationRepository.existsByTicketIdAndAcademicYear(
                ticket.getTicketId(), academicYear)) {
            return false;
        }

        OverdueViolationEntity violation = new OverdueViolationEntity();
        violation.setUserId(ticket.getUserId());
        violation.setTicketId(ticket.getTicketId());
        violation.setAcademicYear(academicYear);
        violation.setOccurredAt(occurredAt);
        violationRepository.save(violation);

        int count = (int) violationRepository.countByUserIdAndAcademicYear(ticket.getUserId(), academicYear);
        user.setViolationAcademicYear(academicYear);
        user.setOverdueViolationCount(count);
        if (count >= 3) {
            user.setCirculationLockedUntil(occurredAt.plusMonths(3));
            user.setCirculationLockReason("Quá hạn 3 lần trong năm học " + academicYear);
            notificationService.createOnce(
                    user.getUserId(), "CIRCULATION_LOCKED",
                    "Quyền mượn, đặt giữ và gia hạn bị khóa đến "
                            + user.getCirculationLockedUntil().toLocalDate()
                            + " do quá hạn 3 lần trong năm học " + academicYear + ".",
                    "USER", user.getUserId().toString(),
                    "CIRCULATION_LOCKED:" + academicYear, occurredAt);
        }
        userRepository.save(user);
        return true;
    }

    String academicYear(LocalDate date) {
        LocalDate startThisYear = LocalDate.of(date.getYear(), academicYearStartMonth, academicYearStartDay);
        int startYear = date.isBefore(startThisYear) ? date.getYear() - 1 : date.getYear();
        return startYear + "-" + (startYear + 1);
    }
}
