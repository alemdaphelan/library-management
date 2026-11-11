package com.huit.library.modules.notification.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.huit.library.modules.notification.entity.PushDeviceRegistrationEntity;
import com.huit.library.modules.notification.repository.PushDeviceRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebasePushNotificationServiceTest {
    @Test
    void sendsPushMessageToActiveDevicesWhenPushIsEnabled() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<FirebaseMessaging> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(messaging);

        PushDeviceRegistrationRepository deviceRepository =
                mock(PushDeviceRegistrationRepository.class);
        NotificationPreferenceService preferenceService = mock(NotificationPreferenceService.class);
        UUID userId = UUID.randomUUID();
        PushDeviceRegistrationEntity device = new PushDeviceRegistrationEntity();
        device.setUserId(userId);
        device.setToken("fcm-valid-token");
        device.setActive(true);

        when(preferenceService.isPushEnabled(userId)).thenReturn(true);
        when(deviceRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of(device));

        FirebasePushNotificationService service =
                new FirebasePushNotificationService(provider, deviceRepository, preferenceService);
        service.send(
                new PushNotificationEvent(
                        userId, "LOAN_APPROVED", "Phiếu #15 đã được duyệt", "LOAN_TICKET", "15"));

        var captured = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messaging).send(captured.capture());
        assertThat(
                        org.springframework.test.util.ReflectionTestUtils.getField(
                                captured.getValue(), "token"))
                .isEqualTo("fcm-valid-token");
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void routesInstallationIdToFidTargetInsteadOfLegacyToken() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<FirebaseMessaging> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(messaging);
        var repository = mock(PushDeviceRegistrationRepository.class);
        var preferences = mock(NotificationPreferenceService.class);
        UUID user = UUID.randomUUID();
        var device = new PushDeviceRegistrationEntity();
        device.setToken("installation-id");
        device.setIdentifierType("FID");
        when(preferences.isPushEnabled(user)).thenReturn(true);
        when(repository.findByUserIdAndActiveTrue(user)).thenReturn(List.of(device));
        new FirebasePushNotificationService(provider, repository, preferences)
                .send(
                        new PushNotificationEvent(
                                user, "LOAN_APPROVED", "Approved", "LOAN_TICKET", "15"));
        var captured = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messaging).send(captured.capture());
        assertThat(
                        org.springframework.test.util.ReflectionTestUtils.getField(
                                captured.getValue(), "fid"))
                .isEqualTo("installation-id");
        assertThat(
                        org.springframework.test.util.ReflectionTestUtils.getField(
                                captured.getValue(), "token"))
                .isNull();
    }

    @Test
    void deactivatesDeviceTokenWhenFirebaseReportsUnregisteredOrInvalid() throws Exception {
        FirebaseMessaging messaging = mock(FirebaseMessaging.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<FirebaseMessaging> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(messaging);

        PushDeviceRegistrationRepository deviceRepository =
                mock(PushDeviceRegistrationRepository.class);
        NotificationPreferenceService preferenceService = mock(NotificationPreferenceService.class);
        UUID userId = UUID.randomUUID();
        PushDeviceRegistrationEntity expiredDevice = new PushDeviceRegistrationEntity();
        expiredDevice.setUserId(userId);
        expiredDevice.setToken("fcm-expired-token");
        expiredDevice.setActive(true);

        FirebaseMessagingException unregisteredError = mock(FirebaseMessagingException.class);
        when(unregisteredError.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(unregisteredError);
        when(preferenceService.isPushEnabled(userId)).thenReturn(true);
        when(deviceRepository.findByUserIdAndActiveTrue(userId)).thenReturn(List.of(expiredDevice));

        FirebasePushNotificationService service =
                new FirebasePushNotificationService(provider, deviceRepository, preferenceService);
        service.send(
                new PushNotificationEvent(
                        userId, "LOAN_OVERDUE", "Sách quá hạn 1 ngày", "LOAN_DETAIL", "22"));

        assertThat(expiredDevice.getActive()).isFalse();
        verify(deviceRepository).save(expiredDevice);
    }
}
