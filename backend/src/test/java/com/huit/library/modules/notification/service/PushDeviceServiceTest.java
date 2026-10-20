package com.huit.library.modules.notification.service;

import com.huit.library.modules.notification.dto.RegisterPushDeviceRequestDTO;
import com.huit.library.modules.notification.entity.PushDeviceRegistrationEntity;
import com.huit.library.modules.notification.repository.PushDeviceRegistrationRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
        assertThat(response.active()).isTrue();
        verify(repository).save(existing);
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
