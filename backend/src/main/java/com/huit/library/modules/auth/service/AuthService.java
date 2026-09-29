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
        String refreshToken = tokenService.generateAndStoreRefreshToken(userDetails.getUser().getEmail());

        return new JwtResponseDTO(jwt, refreshToken, userDetails.getUser().getRoleId());
    }

    public void changePassword(String email, String oldPassword, String newPassword) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, oldPassword));
        } catch (org.springframework.security.authentication.BadCredentialsException e) {
            throw new IllegalArgumentException("Mật khẩu cũ không chính xác");
        }
                
        UserEntity user = userRepository.findFirstByEmailOrStudentId(email, email).orElseThrow();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    public JwtResponseDTO refreshToken(String refreshToken) {
        String email = tokenService.getEmailFromRefreshToken(refreshToken);
        if (email == null) {
            throw new RuntimeException("Invalid refresh token");
        }
        
        UserEntity user = userRepository.findFirstByEmailOrStudentId(email, email).orElseThrow(() -> new RuntimeException("User not found"));
        
        org.springframework.security.core.userdetails.UserDetails userDetails = 
                new com.huit.library.core.security.CustomUserDetails(user);
                
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
                
        String newJwt = jwtUtils.generateJwtToken(authentication);
        String newRefreshToken = tokenService.generateAndStoreRefreshToken(email);
        tokenService.deleteRefreshToken(refreshToken);
        
        return new JwtResponseDTO(newJwt, newRefreshToken, user.getRoleId());
    }

    public void forgotPassword(String email) {
        UserEntity user = userRepository.findFirstByEmailOrStudentId(email, email)
                .orElseThrow(() -> new RuntimeException("Email không tồn tại trong hệ thống"));
        
        // Generate 6-digit OTP
        String otp = String.format("%06d", new Random().nextInt(999999));
        
        // Store in Redis with 10 mins expiry
        redisTemplate.opsForValue().set("OTP_" + email, otp, 10, TimeUnit.MINUTES);
        
        // Send email
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(user.getEmail() != null && !user.getEmail().isEmpty() ? user.getEmail() : email);
        message.setSubject("Mã xác nhận khôi phục mật khẩu (Library Management)");
        message.setText("Xin chào " + user.getFullName() + ",\n\n" +
                "Mã xác nhận (OTP) của bạn là: " + otp + "\n" +
                "Mã này có hiệu lực trong 10 phút. Vui lòng không chia sẻ mã này cho bất kỳ ai.\n\n" +
                "Trân trọng,\nThư viện HUIT.");
        
        try {
            mailSender.send(message);
        } catch (Exception e) {
            System.err.println("Lỗi gửi email: " + e.getMessage());
            // Still log to console for testing without real SMTP
            System.out.println("OTP cho " + email + " là: " + otp);
        }
    }

    public boolean verifyOtp(String email, String otp) {
        String storedOtp = redisTemplate.opsForValue().get("OTP_" + email);
        if (storedOtp != null && storedOtp.equals(otp)) {
            return true;
        }
        throw new IllegalArgumentException("Mã OTP không hợp lệ hoặc đã hết hạn");
    }

    public void resetPassword(String email, String otp, String newPassword) {
        if (verifyOtp(email, otp)) {
            UserEntity user = userRepository.findFirstByEmailOrStudentId(email, email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            redisTemplate.delete("OTP_" + email);
        }
    }
}
