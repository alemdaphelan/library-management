package com.huit.library.modules.notification.service;

import com.huit.library.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailNotificationServiceTest {
    @Test
    void sendsFromConfiguredMailboxToUsersStoredEmail() {
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        UserRepository users = mock(UserRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        com.huit.library.modules.auth.entity.UserEntity user =
                mock(com.huit.library.modules.auth.entity.UserEntity.class);
        UUID userId = UUID.randomUUID();
        when(preferences.isEmailEnabled(userId)).thenReturn(true);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(user.getEmail()).thenReturn("nguyenbaohan8910@gmail.com");
        EmailNotificationService service =
                new EmailNotificationService(
                        preferences, users, mailSender, "lamto20050505@gmail.com");

        service.send(new PushNotificationEvent(userId, "DUE_SOON", "Sắp đến hạn", "LOAN", "1"));

        var captor = forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals("lamto20050505@gmail.com", captor.getValue().getFrom());
        assertEquals("nguyenbaohan8910@gmail.com", captor.getValue().getTo()[0]);
    }

    @Test
    void doesNotLookUpOrSendEmailWhenUserDisabledChannel() {
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        UserRepository users = mock(UserRepository.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        UUID userId = UUID.randomUUID();
        when(preferences.isEmailEnabled(userId)).thenReturn(false);
        EmailNotificationService service =
                new EmailNotificationService(
                        preferences, users, mailSender, "lamto20050505@gmail.com");

        service.send(new PushNotificationEvent(userId, "DUE_SOON", "Sắp đến hạn", "LOAN", "1"));

        verify(users, never()).findById(userId);
        verify(mailSender, never())
                .send(
                        org.mockito.ArgumentMatchers.any(
                                org.springframework.mail.SimpleMailMessage.class));
    }
}
