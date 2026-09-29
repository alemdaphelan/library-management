package com.huit.library.core.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class TokenService {

    private final StringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String REFRESH_TOKEN_PREFIX = "jwt:refresh:";

    // In a real application, these should be configured via application.yml
    private final long refreshTokenDurationMs = 2592000000L; // 30 days
    private final long jwtExpirationMs = 86400000L; // 1 day

    public TokenService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void blacklistToken(String jwt) {
        redisTemplate.opsForValue().set(BLACKLIST_PREFIX + jwt, "true", Duration.ofMillis(jwtExpirationMs));
    }

    public boolean isTokenBlacklisted(String jwt) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + jwt));
    }

    public String generateAndStoreRefreshToken(String email) {
        String refreshToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + refreshToken, email,
                Duration.ofMillis(refreshTokenDurationMs));
        return refreshToken;
    }

    public String getEmailFromRefreshToken(String refreshToken) {
        return redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + refreshToken);
    }

    public void deleteRefreshToken(String refreshToken) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
    }
}
