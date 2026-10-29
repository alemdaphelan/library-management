package com.huit.library.modules.auth.service;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.core.security.JwtUtils;
import com.huit.library.modules.auth.dto.JwtResponseDTO;
import com.huit.library.modules.auth.dto.LoginRequestDTO;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.exception.OtpDeliveryException;
import com.huit.library.modules.auth.repository.UserRepository;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final String OTP_PREFIX = "auth:otp:";
    private static final String OTP_RESEND_PREFIX = "auth:otp:resend:";
    private static final String OTP_ATTEMPTS_PREFIX = "auth:otp:attempts:";
    private static final String OTP_LOCK_PREFIX = "auth:otp:lock:";
    private static final long OTP_TTL_MINUTES = 10;
    private static final long OTP_RESEND_SECONDS = 10;
    private static final long OTP_LOCK_MINUTES = 15;
    private static final long OTP_MAX_ATTEMPTS = 5;

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.huit.library.core.security.TokenService tokenService;
    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    private final String fromEmail;

    @org.springframework.beans.factory.annotation.Autowired
    public AuthService(AuthenticationManager authenticationManager, JwtUtils jwtUtils,
            UserRepository userRepository, PasswordEncoder passwordEncoder,
            com.huit.library.core.security.TokenService tokenService,
            JavaMailSender mailSender, StringRedisTemplate redisTemplate,
            @org.springframework.beans.factory.annotation.Value("${spring.mail.username:}") String fromEmail) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
        this.fromEmail = fromEmail;
    }

    public JwtResponseDTO authenticateUser(LoginRequestDTO loginRequest) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(loginRequest.getEmail(), loginRequest.getPassword()));

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String jwt = jwtUtils.generateJwtToken(authentication);

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        UserEntity user = userDetails.getUser();
        String refreshToken = tokenService.generateAndStoreRefreshToken(user.getEmail());

        return JwtResponseDTO.builder()
                .tokenType("Bearer")
                .accessToken(jwt)
                .refreshToken(refreshToken)
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mssv(user.getMssv())
                .role(user.getRoleId())
                .isFirstLogin(user.getIsFirstLogin())
                .avatarUrl(user.getAvatarUrl())
                .cardStatus(user.getCardStatus())
                .build();
    }

    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Tài khoản không tồn tại"));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu cũ không chính xác");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setIsFirstLogin(false);
        userRepository.save(user);
    }

    public JwtResponseDTO refreshToken(String refreshToken) {
        String email = tokenService.getEmailFromRefreshToken(refreshToken);
        if (email == null) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        UserEntity user = userRepository.findFirstByEmailOrMssv(email, email)
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        org.springframework.security.core.userdetails.UserDetails userDetails = new com.huit.library.core.security.CustomUserDetails(
                user);

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        String newJwt = jwtUtils.generateJwtToken(authentication);
        String newRefreshToken = tokenService.generateAndStoreRefreshToken(email);
        tokenService.deleteRefreshToken(refreshToken);

        return JwtResponseDTO.builder()
                .tokenType("Bearer")
                .accessToken(newJwt)
                .refreshToken(newRefreshToken)
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .mssv(user.getMssv())
                .role(user.getRoleId())
                .isFirstLogin(user.getIsFirstLogin())
                .avatarUrl(user.getAvatarUrl())
                .cardStatus(user.getCardStatus())
                .build();
    }

    public void forgotPassword(String email) {
        String input = normalizeIdentifier(email);
        if (input == null || !acquireResendWindow(input)) {
            return;
        }

        UserEntity user = userRepository.findFirstByEmailOrMssv(email.trim(), email.trim()).orElse(null);
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        String primaryEmail = normalizeIdentifier(user.getEmail());
        if (!primaryEmail.equals(input) && !acquireResendWindow(primaryEmail)) {
            return;
        }

        // Generate secure 6-digit OTP (100000 - 999999)
        String otp = String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));

        // Store in Redis with 10 mins expiry under user's email and user's input key
        for (String identifier : identifiers(input, primaryEmail)) {
            redisTemplate.opsForValue().set(OTP_PREFIX + identifier, otp, OTP_TTL_MINUTES, TimeUnit.MINUTES);
            redisTemplate.delete(OTP_ATTEMPTS_PREFIX + identifier);
            redisTemplate.delete(OTP_LOCK_PREFIX + identifier);
        }

        // Send email
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromEmail != null && !fromEmail.isBlank()) {
            message.setFrom(fromEmail);
        }
        message.setTo(user.getEmail() != null && !user.getEmail().isEmpty() ? user.getEmail() : email);
        message.setSubject("Mã xác nhận khôi phục mật khẩu (Library Management)");
        message.setText("Xin chào " + user.getFullName() + ",\n\n" +
                "Mã xác nhận (OTP) của bạn là: " + otp + "\n" +
                "Mã này có hiệu lực trong 10 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.\n\n" +
                "Trân trọng,\nThư viện HUIT.");

        try {
            // Sending before returning prevents the request from reporting success when the
            // mail server has not accepted the OTP yet. It also keeps failures observable
            // in
            // integration tests and deployment logs instead of losing them in a detached
            // thread.
            mailSender.send(message);
        } catch (Exception e) {
            LOGGER.error("Không thể gửi email OTP", e);
            // Không giữ OTP/resend lock khi máy chủ email chưa nhận thư. Người dùng
            // có thể thử lại ngay sau khi cấu hình SMTP được khắc phục, và API cũng
            // không báo thành công giả.
            for (String identifier : identifiers(input, primaryEmail)) {
                redisTemplate.delete(OTP_PREFIX + identifier);
                redisTemplate.delete(OTP_ATTEMPTS_PREFIX + identifier);
                redisTemplate.delete(OTP_LOCK_PREFIX + identifier);
                redisTemplate.delete(OTP_RESEND_PREFIX + identifier);
            }
            throw new OtpDeliveryException(
                    "Không thể gửi mã OTP lúc này. Vui lòng thử lại sau.", e);
        }
    }

    public boolean verifyOtp(String email, String otp) {
        if (email == null || otp == null) {
            throw new IllegalArgumentException("Thông tin xác thực không hợp lệ");
        }
        String input = normalizeIdentifier(email);
        UserEntity user = userRepository.findFirstByEmailOrMssv(email.trim(), email.trim()).orElse(null);
        String primaryEmail = user == null ? null : normalizeIdentifier(user.getEmail());
        Set<String> identifiers = identifiers(input, primaryEmail);

        if (identifiers.stream().anyMatch(identifier -> Boolean.TRUE.equals(
                redisTemplate.hasKey(OTP_LOCK_PREFIX + identifier)))) {
            throw new IllegalArgumentException("Bạn đã nhập sai OTP quá nhiều lần. Vui lòng thử lại sau 15 phút");
        }

        String storedOtp = identifiers.stream()
                .map(identifier -> redisTemplate.opsForValue().get(OTP_PREFIX + identifier))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);

        if (storedOtp != null && storedOtp.equals(otp.trim())) {
            identifiers.forEach(identifier -> redisTemplate.delete(OTP_ATTEMPTS_PREFIX + identifier));
            return true;
        }

        String attemptIdentifier = primaryEmail == null ? input : primaryEmail;
        Long attempts = redisTemplate.opsForValue().increment(OTP_ATTEMPTS_PREFIX + attemptIdentifier);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(OTP_ATTEMPTS_PREFIX + attemptIdentifier, OTP_TTL_MINUTES, TimeUnit.MINUTES);
        }
        if (attempts != null && attempts >= OTP_MAX_ATTEMPTS) {
            for (String identifier : identifiers) {
                redisTemplate.opsForValue().set(OTP_LOCK_PREFIX + identifier, "1", OTP_LOCK_MINUTES,
                        TimeUnit.MINUTES);
                redisTemplate.delete(OTP_PREFIX + identifier);
            }
            throw new IllegalArgumentException("Bạn đã nhập sai OTP quá nhiều lần. Vui lòng thử lại sau 15 phút");
        }
        throw new IllegalArgumentException("Mã OTP không hợp lệ hoặc đã hết hạn");
    }

    public void resetPassword(String email, String otp, String newPassword) {
        if (verifyOtp(email, otp)) {
            UserEntity user = userRepository.findFirstByEmailOrMssv(email, email)
                    .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.save(user);

            // Delete all password-recovery state after a successful reset.
            for (String identifier : identifiers(normalizeIdentifier(email), normalizeIdentifier(user.getEmail()))) {
                redisTemplate.delete(OTP_PREFIX + identifier);
                redisTemplate.delete(OTP_ATTEMPTS_PREFIX + identifier);
                redisTemplate.delete(OTP_LOCK_PREFIX + identifier);
                redisTemplate.delete(OTP_RESEND_PREFIX + identifier);
            }
        }
    }

    private boolean acquireResendWindow(String identifier) {
        return !Boolean.FALSE.equals(redisTemplate.opsForValue().setIfAbsent(
                OTP_RESEND_PREFIX + identifier, "1", OTP_RESEND_SECONDS, TimeUnit.SECONDS));
    }

    private String normalizeIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        return identifier.trim().toLowerCase(Locale.ROOT);
    }

    private Set<String> identifiers(String input, String primaryEmail) {
        Set<String> identifiers = new LinkedHashSet<>();
        if (input != null)
            identifiers.add(input);
        if (primaryEmail != null)
            identifiers.add(primaryEmail);
        return identifiers;
    }

    public UserEntity getFreshUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public UserEntity updateProfile(UUID userId, com.huit.library.modules.auth.dto.UpdateProfileRequestDTO request) {
        UserEntity user = getFreshUser(userId);
        if (request.getFullName() != null && !request.getFullName().trim().isEmpty()) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone().trim());
        }
        if (request.getAvatarUrl() != null && !request.getAvatarUrl().trim().isEmpty()) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        return userRepository.save(user);
    }
}
