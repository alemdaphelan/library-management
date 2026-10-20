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

    public FirebasePushNotificationService(
            ObjectProvider<FirebaseMessaging> messagingProvider,
            PushDeviceRegistrationRepository deviceRepository) {
        this.messagingProvider = messagingProvider;
        this.deviceRepository = deviceRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void send(PushNotificationEvent event) {
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

                Message message = Message.builder()
                        .setToken(device.getToken())
                        .setNotification(com.google.firebase.messaging.Notification.builder()
                                .setTitle(titleFor(event.type()))
                                .setBody(event.content())
                                .build())
                        .putAllData(data)
                        .build();
                messaging.send(message);
            } catch (FirebaseMessagingException exception) {
                if (exception.getMessagingErrorCode() != null &&
                        (exception.getMessagingErrorCode().name().equals("UNREGISTERED") ||
                         exception.getMessagingErrorCode().name().equals("INVALID_ARGUMENT"))) {
                    device.setActive(false);
                    deviceRepository.save(device);
                }
                LOGGER.warn("Could not send Firebase notification to a registered device: {}",
                        exception.getMessage());
            }
        }
    }

    private String titleFor(String type) {
        if (type == null) return "Thư viện HUIT";
        return switch (type.toUpperCase()) {
            case "DUE_SOON" -> "Sắp đến hạn trả sách";
            case "DUE_TODAY" -> "Đến hạn trả sách";
            case "OVERDUE" -> "Sách đang quá hạn";
            case "PENALTY", "FINE" -> "Phát sinh khoản phí";
            case "ACCOUNT_LOCKED" -> "Tài khoản bị khóa";
            case "ACCOUNT_UNLOCKED" -> "Tài khoản đã mở lại";
            case "WAITLIST_READY" -> "Sách đặt giữ đã sẵn sàng";
            default -> "Thông báo Thư viện HUIT";
        };
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
