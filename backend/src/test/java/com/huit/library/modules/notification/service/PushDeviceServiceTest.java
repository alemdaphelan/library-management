package com.huit.library.modules.notification.service;

import com.huit.library.modules.notification.dto.RegisterPushDeviceRequestDTO;
import com.huit.library.modules.notification.entity.PushDeviceRegistrationEntity;
import com.huit.library.modules.notification.repository.PushDeviceRegistrationRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushDeviceServiceTest {
    @Test
    void reassignsExistingTokenAndActivatesIt() {
        PushDeviceRegistrationRepository repository =
                mock(PushDeviceRegistrationRepository.class);
        PushDeviceRegistrationEntity existing = new PushDeviceRegistrationEntity();
        existing.setRegistrationId(UUID.randomUUID());
        existing.setToken("token-1");
        existing.setActive(false);
        when(repository.findByToken("token-1")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        PushDeviceService service = new PushDeviceService(repository);
        UUID userId = UUID.randomUUID();

        var response = service.register(userId,
                new RegisterPushDeviceRequestDTO(" token-1 ", "ANDROID", "phone-a"));

        assertThat(existing.getUserId()).isEqualTo(userId);
        assertThat(existing.getActive()).isTrue();
        assertThat(existing.getIdentifierType()).isEqualTo("TOKEN");
        assertThat(response.active()).isTrue();
        verify(repository).save(existing);
    }

    @Test
    void updatesTokenInPlaceWhenFirebaseRefreshesTokenForSameUserAndDevice() {
        PushDeviceRegistrationRepository repository =
                mock(PushDeviceRegistrationRepository.class);
        UUID userId = UUID.randomUUID();
        PushDeviceRegistrationEntity existingDevice = new PushDeviceRegistrationEntity();
        existingDevice.setRegistrationId(UUID.randomUUID());
        existingDevice.setUserId(userId);
        existingDevice.setDeviceId("device-01");
        existingDevice.setToken("old-token");
        existingDevice.setActive(true);

        when(repository.findByToken("new-token")).thenReturn(Optional.empty());
        when(repository.findByDeviceIdAndActiveTrue("device-01")).thenReturn(List.of(existingDevice));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PushDeviceService service = new PushDeviceService(repository);
        var response = service.register(userId,
                new RegisterPushDeviceRequestDTO("new-token", "ANDROID", "device-01", "FID"));

        assertThat(existingDevice.getToken()).isEqualTo("new-token");
        assertThat(existingDevice.getIdentifierType()).isEqualTo("FID");
        assertThat(existingDevice.getUserId()).isEqualTo(userId);
        assertThat(existingDevice.getActive()).isTrue();
        assertThat(response.registrationId()).isEqualTo(existingDevice.getRegistrationId());
    }

    @Test
    void deactivatesOldAccountRegistrationWhenAnotherAccountLogsInOnSameDevice() {
        PushDeviceRegistrationRepository repository =
                mock(PushDeviceRegistrationRepository.class);
        UUID previousUserId = UUID.randomUUID();
        UUID newUserId = UUID.randomUUID();
        PushDeviceRegistrationEntity oldAccountDevice = new PushDeviceRegistrationEntity();
        oldAccountDevice.setRegistrationId(UUID.randomUUID());
        oldAccountDevice.setUserId(previousUserId);
        oldAccountDevice.setDeviceId("shared-emulator");
        oldAccountDevice.setToken("old-user-token");
        oldAccountDevice.setActive(true);

        when(repository.findByToken("new-user-token")).thenReturn(Optional.empty());
        when(repository.findByDeviceIdAndActiveTrue("shared-emulator"))
                .thenReturn(List.of(oldAccountDevice));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PushDeviceService service = new PushDeviceService(repository);
        service.register(newUserId,
                new RegisterPushDeviceRequestDTO("new-user-token", "ANDROID", "shared-emulator"));

        assertThat(oldAccountDevice.getActive()).isFalse();
    }

    @Test
    void unregisterOnlyDeactivatesTokenOwnedByCurrentUser() {
        PushDeviceRegistrationRepository repository =
                mock(PushDeviceRegistrationRepository.class);
        PushDeviceRegistrationEntity existing = new PushDeviceRegistrationEntity();
        existing.setToken("token-1");
        existing.setActive(true);
        UUID userId = UUID.randomUUID();
        when(repository.findByUserIdAndToken(userId, "token-1"))
                .thenReturn(Optional.of(existing));
        PushDeviceService service = new PushDeviceService(repository);

        service.unregister(userId, " token-1 ");

        assertThat(existing.getActive()).isFalse();
        verify(repository).save(existing);
    }
}
