package com.huit.library.modules.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.huit.library.modules.notification.entity.PushDeviceRegistrationEntity;
import com.huit.library.modules.notification.repository.PushDeviceRegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class FirebasePushNotificationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FirebasePushNotificationService.class);

    private final ObjectProvider<FirebaseMessaging> messagingProvider;
    private final PushDeviceRegistrationRepository deviceRepository;
    private final NotificationPreferenceService preferenceService;

    public FirebasePushNotificationService(
            ObjectProvider<FirebaseMessaging> messagingProvider,
            PushDeviceRegistrationRepository deviceRepository,
            NotificationPreferenceService preferenceService) {
        this.messagingProvider = messagingProvider;
        this.deviceRepository = deviceRepository;
        this.preferenceService = preferenceService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Async("notificationDeliveryExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(PushNotificationEvent event) {
        if (!preferenceService.isPushEnabled(event.userId())) {
            return;
        }
        FirebaseMessaging messaging = messagingProvider.getIfAvailable();
        if (messaging == null) {
            LOGGER.debug("Firebase push is disabled; notification remains available in-app");
            return;
        }

        List<PushDeviceRegistrationEntity> devices =
                deviceRepository.findByUserIdAndActiveTrue(event.userId());
        for (PushDeviceRegistrationEntity device : devices) {
            try {
                Map<String, String> data = new HashMap<>();
                data.put("type", nullToEmpty(event.type()));
                data.put("referenceType", nullToEmpty(event.referenceType()));
                data.put("referenceId", nullToEmpty(event.referenceId()));

                Message.Builder builder = Message.builder()
                        .setNotification(com.google.firebase.messaging.Notification.builder()
                                .setTitle(titleFor(event.type()))
                                .setBody(event.content())
                                .build())
                        .putAllData(data);
                if ("FID".equals(device.getIdentifierType())) builder.setFid(device.getToken());
                else builder.setToken(device.getToken());
                Message message = builder.build();
                messaging.send(message);
            } catch (FirebaseMessagingException exception) {
                if (isPermanentTokenError(exception)) {
                    device.setActive(false);
                    deviceRepository.save(device);
                }
                LOGGER.warn("Could not send Firebase notification to a registered device: {}",
                        exception.getMessage());
            } catch (RuntimeException exception) {
                LOGGER.warn("Unexpected error sending Firebase notification to registered device: {}",
                        exception.getMessage());
            }
        }
    }

    private boolean isPermanentTokenError(FirebaseMessagingException exception) {
        if (exception.getMessagingErrorCode() == null) {
            return false;
        }
        String code = exception.getMessagingErrorCode().name();
        return "UNREGISTERED".equals(code)
                || "INVALID_ARGUMENT".equals(code)
                || "SENDER_ID_MISMATCH".equals(code);
    }

    private String titleFor(String type) {
        if (type == null) return "Thư viện HUIT";
        return switch (type.toUpperCase()) {
            case "DUE_SOON", "LOAN_DUE_SOON" -> "Sắp đến hạn trả sách";
            case "DUE_TODAY", "LOAN_DUE_TODAY" -> "Đến hạn trả sách";
            case "OVERDUE", "LOAN_OVERDUE" -> "Sách đang quá hạn";
            case "LOAN_APPROVED" -> "Yêu cầu mượn đã được duyệt";
            case "LOAN_REJECTED" -> "Yêu cầu mượn bị từ chối";
            case "WAITLIST_READY", "WAITLIST_AVAILABLE" -> "Sách đặt giữ đã sẵn sàng";
            case "RESERVATION_EXPIRED" -> "Phiếu đặt giữ đã hết hạn";
            case "RESERVATION_NO_SHOW" -> "Cảnh báo không đến nhận tài liệu";
            case "PENALTY", "FINE", "PENALTY_CASE_CREATED", "PENALTY_CASE_DUE" -> "Phát sinh khoản phí / đền bù";
            case "WAIVER_DECISION" -> "Kết quả xét miễn giảm khoản phạt";
            case "ACCOUNT_LOCKED", "CIRCULATION_LOCKED" -> "Tài khoản bị khóa";
            case "ACCOUNT_UNLOCKED", "CIRCULATION_UNLOCKED" -> "Tài khoản đã mở lại";
            case "PROPOSAL_APPROVED" -> "Đề xuất tài liệu đã được chấp nhận";
            case "PROPOSAL_REJECTED" -> "Kết quả đề xuất tài liệu";
            default -> "Thông báo Thư viện HUIT";
        };
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
