package com.huit.library.modules.auth.service;

import com.huit.library.core.security.CustomUserDetails;
import com.huit.library.core.security.JwtUtils;
import com.huit.library.modules.auth.dto.JwtResponseDTO;
import com.huit.library.modules.auth.dto.LoginRequestDTO;
import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.huit.library.core.security.TokenService tokenService;
    private final JavaMailSender mailSender;
    private final StringRedisTemplate redisTemplate;

    public AuthService(AuthenticationManager authenticationManager, JwtUtils jwtUtils, 
                       UserRepository userRepository, PasswordEncoder passwordEncoder,
                       com.huit.library.core.security.TokenService tokenService,
                       JavaMailSender mailSender, StringRedisTemplate redisTemplate) {
        this.authenticationManager = authenticationManager;
        this.jwtUtils = jwtUtils;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.mailSender = mailSender;
        this.redisTemplate = redisTemplate;
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

    public void changePassword(String email, String oldPassword, String newPassword) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, oldPassword));
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            throw new IllegalArgumentException("Mật khẩu cũ không chính xác");
        }
                
        UserEntity user = userRepository.findFirstByEmailOrMssv(email, email).orElseThrow();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public JwtResponseDTO refreshToken(String refreshToken) {
        String email = tokenService.getEmailFromRefreshToken(refreshToken);
        if (email == null) {
            throw new RuntimeException("Invalid refresh token");
        }
        
        UserEntity user = userRepository.findFirstByEmailOrMssv(email, email).orElseThrow(() -> new RuntimeException("User not found"));
        
        org.springframework.security.core.userdetails.UserDetails userDetails = 
                new com.huit.library.core.security.CustomUserDetails(user);
                
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

    public String forgotPassword(String email) {
        UserEntity user = userRepository.findFirstByEmailOrMssv(email, email)
                .orElseThrow(() -> new RuntimeException("Email/MSSV không tồn tại trong hệ thống"));
        
        // Generate secure 6-digit OTP (100000 - 999999)
        java.security.SecureRandom secureRandom = new java.security.SecureRandom();
        String otp = String.valueOf(100000 + secureRandom.nextInt(900000));
        
        // Store in Redis with 10 mins expiry under user's email and user's input key
        String primaryEmail = user.getEmail() != null ? user.getEmail().toLowerCase().trim() : email.toLowerCase().trim();
        redisTemplate.opsForValue().set("OTP_" + primaryEmail, otp, 10, TimeUnit.MINUTES);
        if (!email.toLowerCase().trim().equals(primaryEmail)) {
            redisTemplate.opsForValue().set("OTP_" + email.toLowerCase().trim(), otp, 10, TimeUnit.MINUTES);
        }
        
        // Send email
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail() != null && !user.getEmail().isEmpty() ? user.getEmail() : email);
        message.setSubject("Mã xác nhận khôi phục mật khẩu (Library Management)");
        message.setText("Xin chào " + user.getFullName() + ",\n\n" +
                "Mã xác nhận (OTP) của bạn là: " + otp + "\n" +
                "Mã này có hiệu lực trong 10 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.\n\n" +
                "Trân trọng,\nThư viện HUIT.");
        
        new Thread(() -> {
            try {
                mailSender.send(message);
            } catch (Exception e) {
                System.err.println("Lỗi gửi email: " + e.getMessage());
                System.out.println("OTP cho " + email + " là: " + otp);
            }
        }).start();
        
        return otp;
    }

    public boolean verifyOtp(String email, String otp) {
        if (email == null || otp == null) {
            throw new IllegalArgumentException("Thông tin xác thực không hợp lệ");
        }
        String inputKey = "OTP_" + email.toLowerCase().trim();
        String storedOtp = redisTemplate.opsForValue().get(inputKey);
        
        // If not found directly by input, try finding user and check against user's email
        if (storedOtp == null) {
            UserEntity user = userRepository.findFirstByEmailOrMssv(email.trim(), email.trim()).orElse(null);
            if (user != null && user.getEmail() != null) {
                storedOtp = redisTemplate.opsForValue().get("OTP_" + user.getEmail().toLowerCase().trim());
            }
        }
        
        if (storedOtp != null && storedOtp.equals(otp.trim())) {
            return true;
        }
        throw new IllegalArgumentException("Mã OTP không hợp lệ hoặc đã hết hạn");
    }

    public void resetPassword(String email, String otp, String newPassword) {
        if (verifyOtp(email, otp)) {
            UserEntity user = userRepository.findFirstByEmailOrMssv(email, email)
                    .orElseThrow(() -> new RuntimeException("Tài khoản không tồn tại"));
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            
            // Delete OTP keys
            redisTemplate.delete("OTP_" + email.toLowerCase().trim());
            if (user.getEmail() != null) {
                redisTemplate.delete("OTP_" + user.getEmail().toLowerCase().trim());
            }
        }
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
