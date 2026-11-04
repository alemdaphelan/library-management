package com.huit.library.core.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    @Test
    void rejectsAccessTokenIssuedBeforePasswordChange() throws Exception {
        JwtUtils jwtUtils = mock(JwtUtils.class);
        CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
        TokenService tokenService = mock(TokenService.class);
        JwtAuthFilter filter = new JwtAuthFilter(jwtUtils, userDetailsService, tokenService);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer old-access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(jwtUtils.validateJwtToken("old-access-token")).thenReturn(true);
        when(tokenService.isTokenBlacklisted("old-access-token")).thenReturn(false);
        when(jwtUtils.getUserNameFromJwtToken("old-access-token")).thenReturn("user@huit.edu.vn");
        when(jwtUtils.getSessionVersionFromJwtToken("old-access-token")).thenReturn(0L);
        when(tokenService.isSessionVersionCurrent("user@huit.edu.vn", 0L)).thenReturn(false);

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(filterChain, never()).doFilter(request, response);
        verify(userDetailsService, never()).loadUserByUsername("user@huit.edu.vn");
    }
}
