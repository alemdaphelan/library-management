package com.huit.library.modules.auth.controller;

import com.huit.library.core.security.TokenService;
import com.huit.library.modules.auth.dto.LogoutRequestDTO;
import com.huit.library.modules.auth.dto.RefreshTokenRequestDTO;
import com.huit.library.modules.auth.dto.ForgotPasswordRequestDTO;
import com.huit.library.modules.auth.exception.OtpDeliveryException;
import com.huit.library.modules.auth.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;

class AuthControllerTest {
    @Test
    void logoutRevokesAccessAndRefreshTokens() {
        TokenService tokenService = mock(TokenService.class);
        AuthController controller = new AuthController(mock(AuthService.class), tokenService);
        LogoutRequestDTO request = new LogoutRequestDTO();
        request.setRefreshToken("refresh-token");

        controller.logout("Bearer access-token", request);

        verify(tokenService).blacklistToken("access-token");
        verify(tokenService).deleteRefreshToken("refresh-token");
    }

    @Test
    void refreshTokenReturnsUnauthorizedForRevokedToken() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService, mock(TokenService.class));
        RefreshTokenRequestDTO request = new RefreshTokenRequestDTO();
        request.setRefreshToken("revoked-refresh-token");
        when(authService.refreshToken("revoked-refresh-token"))
                .thenThrow(new BadCredentialsException("Invalid refresh token"));

        ResponseEntity<?> response = controller.refreshToken(request);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        org.assertj.core.api.Assertions.assertThat(response.getBody())
                .isEqualTo("Refresh token không hợp lệ hoặc đã hết hạn");
    }

    @Test
    void forgotPasswordReturnsServiceUnavailableWhenEmailCannotBeDelivered() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(authService, mock(TokenService.class));
        ForgotPasswordRequestDTO request = new ForgotPasswordRequestDTO();
        request.setEmail("user@huit.edu.vn");
        doThrow(new OtpDeliveryException("Không thể gửi mã OTP lúc này. Vui lòng thử lại sau.",
                new IllegalStateException("smtp down")))
                .when(authService).forgotPassword("user@huit.edu.vn");

        ResponseEntity<?> response = controller.forgotPassword(request);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        org.assertj.core.api.Assertions.assertThat(response.getBody())
                .isEqualTo("Không thể gửi mã OTP lúc này. Vui lòng thử lại sau.");
    }
}
