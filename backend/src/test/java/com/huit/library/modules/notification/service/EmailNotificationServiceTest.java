package com.huit.library.modules.notification.service;

import com.huit.library.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailNotificationServiceTest {
    @Test
    void doesNotLookUpOrSendEmailWhenUserDisabledChannel() {
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        UserRepository users = mock(UserRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        UUID userId = UUID.randomUUID();
        when(preferences.isEmailEnabled(userId)).thenReturn(false);
        EmailNotificationService service = new EmailNotificationService(
                preferences, users, mailSender);

        service.send(new PushNotificationEvent(
                userId, "DUE_SOON", "Sắp đến hạn", "LOAN", "1"));

        verify(users, never()).findById(userId);
        verify(mailSender, never()).send(
                org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
    }
}
