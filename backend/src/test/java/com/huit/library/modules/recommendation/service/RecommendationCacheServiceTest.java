package com.huit.library.modules.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.modules.recommendation.config.RecommendationProperties;
import com.huit.library.modules.recommendation.dto.RecommendationResponseDTO;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationCacheServiceTest {
    @Test
    void roundTripsResponseAndUsesConfiguredTtl() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        RecommendationProperties properties = new RecommendationProperties();
        properties.setCacheTtlMinutes(45);
        RecommendationCacheService service = new RecommendationCacheService(
                redisTemplate, objectMapper, properties);
        RecommendationResponseDTO response = new RecommendationResponseDTO(
                "HYBRID_COLD_START", true, 10, LocalDateTime.now(), List.of());
        String json = objectMapper.writeValueAsString(response);

        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn(json);

        Optional<RecommendationResponseDTO> cached = service.get(UUID.randomUUID(), 10, "fingerprint");
        service.put(UUID.randomUUID(), 10, "fingerprint", response);

        assertThat(cached).isPresent();
        assertThat(cached.orElseThrow().method()).isEqualTo("HYBRID_COLD_START");
        verify(values).set(anyString(), eq(json), eq(Duration.ofMinutes(45)));
    }
}
