package com.huit.library.modules.recommendation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huit.library.modules.recommendation.config.RecommendationProperties;
import com.huit.library.modules.recommendation.dto.RecommendationResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class RecommendationCacheService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationCacheService.class);
    private static final String KEY_PREFIX = "recommendation:hybrid:v1:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RecommendationProperties properties;

    public RecommendationCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            RecommendationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Optional<RecommendationResponseDTO> get(
            UUID userId,
            int limit,
            String interactionFingerprint) {
        try {
            String json = redisTemplate.opsForValue().get(key(userId, limit, interactionFingerprint));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, RecommendationResponseDTO.class));
        } catch (Exception exception) {
            log.warn("Recommendation cache read failed; continuing without cache: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    public void put(
            UUID userId,
            int limit,
            String interactionFingerprint,
            RecommendationResponseDTO response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(
                    key(userId, limit, interactionFingerprint),
                    json,
                    Duration.ofMinutes(Math.max(1, properties.getCacheTtlMinutes())));
        } catch (Exception exception) {
            log.warn("Recommendation cache write failed; response remains usable: {}", exception.getMessage());
        }
    }

    private String key(UUID userId, int limit, String interactionFingerprint) {
        return KEY_PREFIX + userId + ":" + limit + ":" + interactionFingerprint;
    }
}
