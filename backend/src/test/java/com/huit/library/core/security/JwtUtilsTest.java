package com.huit.library.core.security;

import com.huit.library.modules.auth.entity.UserEntity;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtUtilsTest {

    @Test
    void accessTokenCarriesCurrentAccountSessionVersion() {
        TokenService tokenService = mock(TokenService.class);
        when(tokenService.getSessionVersion("user@huit.edu.vn")).thenReturn(7L);
        JwtUtils jwtUtils = new JwtUtils(tokenService);
        ReflectionTestUtils.setField(jwtUtils, "jwtSecret",
                "test-secret-key-that-is-long-enough-for-hmac-sha-256-signing");
        ReflectionTestUtils.setField(jwtUtils, "jwtExpirationMs", 60_000);

        UserEntity user = new UserEntity();
        user.setEmail("user@huit.edu.vn");
        user.setRoleId("STUDENT");
        CustomUserDetails principal = new CustomUserDetails(user);
        var authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        String token = jwtUtils.generateJwtToken(authentication);

        assertThat(jwtUtils.getSessionVersionFromJwtToken(token)).isEqualTo(7L);
        assertThat(jwtUtils.getUserNameFromJwtToken(token)).isEqualTo("user@huit.edu.vn");
    }
}
