package com.huit.library.modules.notification.service;

import com.huit.library.modules.notification.dto.NotificationPreferenceRequestDTO;
import com.huit.library.modules.notification.entity.NotificationPreferenceEntity;
import com.huit.library.modules.notification.repository.NotificationPreferenceRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationPreferenceServiceTest {
    @Test
    void returnsSafeDefaultsWhenUserHasNotSavedPreferences() {
        NotificationPreferenceRepository repository = mock(NotificationPreferenceRepository.class);
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());
        NotificationPreferenceService service = new NotificationPreferenceService(repository);

        var result = service.get(userId);

        assertThat(result.emailEnabled()).isTrue();
        assertThat(result.pushEnabled()).isTrue();
    }

    @Test
    void persistsBothRealDeliveryPreferences() {
        NotificationPreferenceRepository repository = mock(NotificationPreferenceRepository.class);
        UUID userId = UUID.randomUUID();
        when(repository.findById(userId)).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        NotificationPreferenceService service = new NotificationPreferenceService(repository);

        var result = service.update(userId,
                new NotificationPreferenceRequestDTO(false, true));

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.emailEnabled()).isFalse();
        assertThat(result.pushEnabled()).isTrue();
    }
}
