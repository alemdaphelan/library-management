package com.huit.library.core.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenServiceTest {
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> values;
    private TokenService tokenService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        tokenService = new TokenService(redisTemplate);
        ReflectionTestUtils.setField(tokenService, "refreshTokenDurationMs", 2_592_000_000L);
    }

    @Test
    void refreshTokenCarriesCurrentSessionVersion() {
        when(values.get("jwt:session-version:user@example.com")).thenReturn("3");

        tokenService.generateAndStoreRefreshToken("User@Example.com");

        verify(values)
                .set(
                        startsWith("jwt:refresh:"),
                        eq("3|User@Example.com"),
                        eq(Duration.ofMillis(2_592_000_000L)));
    }

    @Test
    void rejectsRefreshTokenFromPreviousSessionVersion() {
        when(values.get("jwt:refresh:old-token")).thenReturn("0|user@example.com");
        when(values.get("jwt:session-version:user@example.com")).thenReturn("1");

        assertThat(tokenService.getEmailFromRefreshToken("old-token")).isNull();

        verify(redisTemplate).delete("jwt:refresh:old-token");
    }

    @Test
    void invalidatingSessionsUsesNormalizedEmailKey() {
        when(values.increment("jwt:session-version:user@example.com")).thenReturn(2L);

        assertThat(tokenService.invalidateAllSessions(" User@Example.com ")).isEqualTo(2L);
    }
}
