package com.huit.library.modules.notification.service;

import com.huit.library.modules.auth.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class EmailNotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotificationService.class);

    private final NotificationPreferenceService preferenceService;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    public EmailNotificationService(
            NotificationPreferenceService preferenceService,
            UserRepository userRepository,
            JavaMailSender mailSender) {
        this.preferenceService = preferenceService;
        this.userRepository = userRepository;
        this.mailSender = mailSender;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void send(PushNotificationEvent event) {
        if (!preferenceService.isEmailEnabled(event.userId())) {
            return;
        }
        userRepository.findById(event.userId()).ifPresent(user -> {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                return;
            }
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(user.getEmail());
                message.setSubject(titleFor(event.type()));
                message.setText(event.content());
                mailSender.send(message);
            } catch (RuntimeException exception) {
                LOGGER.warn("Could not send library email notification: {}", exception.getMessage());
            }
        });
    }

    private String titleFor(String type) {
        if (type == null) return "Thông báo Thư viện HUIT";
        return switch (type.toUpperCase()) {
            case "PROPOSAL_APPROVED" -> "Đề xuất tài liệu đã được chấp nhận";
            case "PROPOSAL_REJECTED" -> "Kết quả đề xuất tài liệu";
            case "DUE_SOON", "LOAN_DUE_SOON" -> "Sắp đến hạn trả sách";
            case "DUE_TODAY" -> "Đến hạn trả sách";
            case "OVERDUE", "LOAN_OVERDUE" -> "Sách đang quá hạn";
            case "PENALTY", "FINE" -> "Phát sinh khoản phí";
            case "ACCOUNT_LOCKED", "CIRCULATION_LOCKED" -> "Tài khoản thư viện bị khóa";
            case "ACCOUNT_UNLOCKED", "CIRCULATION_UNLOCKED" -> "Tài khoản thư viện đã mở lại";
            case "WAITLIST_READY" -> "Sách đặt giữ đã sẵn sàng";
            case "RESERVATION_NO_SHOW" -> "Cảnh báo không đến nhận tài liệu";
            default -> "Thông báo Thư viện HUIT";
        };
    }
}
