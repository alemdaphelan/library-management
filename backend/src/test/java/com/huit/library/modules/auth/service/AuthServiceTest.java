package com.huit.library.modules.auth.service;

import com.huit.library.core.security.JwtUtils;
import com.huit.library.core.security.TokenService;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.exception.OtpDeliveryException;
import com.huit.library.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.MailSendException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private JavaMailSender mailSender;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        mailSender = mock(JavaMailSender.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        authService = new AuthService(
                mock(AuthenticationManager.class), mock(JwtUtils.class), userRepository,
                passwordEncoder, mock(TokenService.class), mailSender, redisTemplate);
    }

    @Test
    void changePasswordUsesAuthenticatedUserIdAndClearsFirstLogin() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setUserId(userId);
        user.setPasswordHash("old-hash");
        user.setIsFirstLogin(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        authService.changePassword(userId, "current-password", "new-password");

        assertThat(user.getPasswordHash()).isEqualTo("new-hash");
        assertThat(user.getIsFirstLogin()).isFalse();
        verify(userRepository).save(user);
    }

    @Test
    void changePasswordRejectsIncorrectCurrentPassword() {
        UUID userId = UUID.randomUUID();
        UserEntity user = new UserEntity();
        user.setPasswordHash("stored-hash");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "stored-hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.changePassword(userId, "wrong", "new-password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("không chính xác");
        verify(userRepository, never()).save(any());
    }

    @Test
    void forgotPasswordThrottlesRepeatedRequestsWithoutRevealingOtp() {
        UserEntity user = new UserEntity();
        user.setEmail("user@huit.edu.vn");
        user.setFullName("Nguyen Van A");
        when(userRepository.findFirstByEmailOrMssv("user@huit.edu.vn", "user@huit.edu.vn"))
                .thenReturn(Optional.of(user));
        when(valueOperations.setIfAbsent("auth:otp:resend:user@huit.edu.vn", "1", 10, TimeUnit.SECONDS))
                .thenReturn(true, false);

        authService.forgotPassword("user@huit.edu.vn");
        authService.forgotPassword("user@huit.edu.vn");

        verify(valueOperations, times(1)).set(startsWith("auth:otp:user@huit.edu.vn"),
                matches("\\d{6}"), eq(10L), eq(TimeUnit.MINUTES));
        verify(mailSender, times(1)).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void forgotPasswordReportsMailFailureAndClearsOtpStateForRetry() {
        UserEntity user = new UserEntity();
        user.setEmail("user@huit.edu.vn");
        user.setFullName("Nguyen Van A");
        when(userRepository.findFirstByEmailOrMssv("user@huit.edu.vn", "user@huit.edu.vn"))
                .thenReturn(Optional.of(user));
        when(valueOperations.setIfAbsent("auth:otp:resend:user@huit.edu.vn", "1", 10, TimeUnit.SECONDS))
                .thenReturn(true);
        doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));

        assertThatThrownBy(() -> authService.forgotPassword("user@huit.edu.vn"))
                .isInstanceOf(OtpDeliveryException.class)
                .hasMessageContaining("Không thể gửi mã OTP");

        verify(redisTemplate).delete("auth:otp:user@huit.edu.vn");
        verify(redisTemplate).delete("auth:otp:resend:user@huit.edu.vn");
    }

    @Test
    void verifyOtpLocksIdentifierAfterFiveIncorrectAttempts() {
        UserEntity user = new UserEntity();
        user.setEmail("user@huit.edu.vn");
        when(userRepository.findFirstByEmailOrMssv("user@huit.edu.vn", "user@huit.edu.vn"))
                .thenReturn(Optional.of(user));
        when(redisTemplate.hasKey("auth:otp:lock:user@huit.edu.vn")).thenReturn(false);
        when(valueOperations.get("auth:otp:user@huit.edu.vn")).thenReturn("123456");
        when(valueOperations.increment("auth:otp:attempts:user@huit.edu.vn"))
                .thenReturn(1L, 2L, 3L, 4L, 5L);

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThatThrownBy(() -> authService.verifyOtp("user@huit.edu.vn", "000000"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("không hợp lệ");
        }
        assertThatThrownBy(() -> authService.verifyOtp("user@huit.edu.vn", "000000"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quá nhiều lần");

        verify(valueOperations).set("auth:otp:lock:user@huit.edu.vn", "1", 15L, TimeUnit.MINUTES);
        verify(redisTemplate).delete("auth:otp:user@huit.edu.vn");
    }
}
