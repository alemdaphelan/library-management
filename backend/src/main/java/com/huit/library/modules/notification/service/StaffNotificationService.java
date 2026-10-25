package com.huit.library.modules.notification.service;

import com.huit.library.modules.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class StaffNotificationService {
    private static final List<String> STAFF_ROLES = List.of("ADMIN", "LIBRARIAN");

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public StaffNotificationService(UserRepository userRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public int notifyAll(
            String type,
            String content,
            String referenceType,
            String referenceId,
            String eventKey,
            LocalDateTime sentAt) {
        int created = 0;
        for (var staff : userRepository.findStaffByRoleIds(STAFF_ROLES)) {
            if (notificationService.createOnce(
                    staff.getUserId(), type, content, referenceType, referenceId,
                    eventKey + ":" + staff.getUserId(), sentAt)) {
                created++;
            }
        }
        return created;
    }
}
