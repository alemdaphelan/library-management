package com.huit.library.core.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Service
public class TokenService {

    private final StringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String REFRESH_TOKEN_PREFIX = "jwt:refresh:";
    private static final String SESSION_VERSION_PREFIX = "jwt:session-version:";
    private static final String REFRESH_VALUE_SEPARATOR = "|";

    @Value("${app.refreshTokenExpirationMs:2592000000}")
    private long refreshTokenDurationMs;

    @Value("${app.jwtExpirationMs:86400000}")
    private long jwtExpirationMs;

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
        long sessionVersion = getSessionVersion(email);
        redisTemplate.opsForValue().set(REFRESH_TOKEN_PREFIX + refreshToken,
                sessionVersion + REFRESH_VALUE_SEPARATOR + email,
                Duration.ofMillis(refreshTokenDurationMs));
        return refreshToken;
    }

    public String getEmailFromRefreshToken(String refreshToken) {
        String storedValue = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + refreshToken);
        if (storedValue == null || storedValue.isBlank()) {
            return null;
        }

        int separator = storedValue.indexOf(REFRESH_VALUE_SEPARATOR);
        long tokenVersion = 0L;
        String email = storedValue;
        if (separator > 0) {
            try {
                tokenVersion = Long.parseLong(storedValue.substring(0, separator));
                email = storedValue.substring(separator + 1);
            } catch (NumberFormatException ignored) {
                deleteRefreshToken(refreshToken);
                return null;
            }
        }

        if (!isSessionVersionCurrent(email, tokenVersion)) {
            deleteRefreshToken(refreshToken);
            return null;
        }
        return email;
    }

    public void deleteRefreshToken(String refreshToken) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + refreshToken);
    }

    public long getSessionVersion(String email) {
        String value = redisTemplate.opsForValue().get(sessionVersionKey(email));
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    public boolean isSessionVersionCurrent(String email, long tokenVersion) {
        return tokenVersion == getSessionVersion(email);
    }

    /**
     * Invalidates every access and refresh token issued under the previous
     * session version. A subsequent login receives tokens carrying the new
     * version without requiring a scan over Redis keys.
     */
    public long invalidateAllSessions(String email) {
        Long version = redisTemplate.opsForValue().increment(sessionVersionKey(email));
        return version == null ? 1L : version;
    }

    private String sessionVersionKey(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        return SESSION_VERSION_PREFIX + normalized;
    }
}
