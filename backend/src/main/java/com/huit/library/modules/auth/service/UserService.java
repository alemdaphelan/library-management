package com.huit.library.modules.auth.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.dto.StudentUpsertRequestDTO;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.WalletRepository;
import com.huit.library.modules.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletRepository walletRepository;
    private final NotificationService notificationService;
    private final BigDecimal defaultDeposit;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            WalletRepository walletRepository,
            BigDecimal defaultDeposit) {
        this(userRepository, passwordEncoder, walletRepository, null, defaultDeposit);
    }

    @Autowired
    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            WalletRepository walletRepository,
            NotificationService notificationService,
            @org.springframework.beans.factory.annotation.Value(
                            "${library.circulation.default-security-deposit:0}")
                    BigDecimal defaultDeposit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.walletRepository = walletRepository;
        this.notificationService = notificationService;
        this.defaultDeposit =
                defaultDeposit == null ? BigDecimal.ZERO : defaultDeposit.max(BigDecimal.ZERO);
    }

    public List<UserEntity> getAllStudents() {
        return userRepository.findByUserTypeOrderByFullNameAsc("STUDENT");
    }

    public UserEntity getStudentByMssv(String mssv) {
        return userRepository
                .findByMssv(mssv)
                .orElseThrow(() -> new RuntimeException("Student not found"));
    }

    @Transactional
    public UserEntity createStudent(StudentUpsertRequestDTO request) {
        requireNoCardChangesBeforeOrientation(request);
        ensureUnique(request.mssv(), request.email(), null);
        UserEntity student = new UserEntity();
        applyProfile(student, request);
        student.setUserType("STUDENT");
        student.setRoleId("STUDENT");
        student.setPasswordHash(passwordEncoder.encode(student.getMssv()));
        student.setIsFirstLogin(true);
        student.setDisciplineFlag(false);
        student = userRepository.save(student);
        ensureWallet(student);
        return student;
    }

    @Transactional
    public UserEntity updateStudent(String mssv, StudentUpsertRequestDTO request) {
        UserEntity existing =
                userRepository
                        .findByMssvAndUserType(mssv, "STUDENT")
                        .orElseThrow(
                                () ->
                                        new ResponseStatusException(
                                                HttpStatus.NOT_FOUND, "Không tìm thấy sinh viên"));
        String previousCardStatus = existing.getCardStatus();
        if (!existing.hasLibraryCard()) {
            requireNoCardChangesBeforeOrientation(request);
        }
        ensureUnique(request.mssv(), request.email(), existing);
        applyProfile(existing, request);
        if (request.cardStatus() != null) {
            existing.setCardStatus(request.cardStatus());
        }
        if (request.cardExpiryDate() != null) {
            existing.setCardExpiryDate(request.cardExpiryDate());
        }
        UserEntity saved = userRepository.save(existing);
        notifyCardStatusChangeIfNeeded(saved, previousCardStatus);
        return saved;
    }

    private void notifyCardStatusChangeIfNeeded(UserEntity saved, String previousCardStatus) {
        if (notificationService == null || saved == null || saved.getUserId() == null) {
            return;
        }
        String prev =
                previousCardStatus == null || previousCardStatus.isBlank()
                        ? "ACTIVE"
                        : previousCardStatus.trim().toUpperCase();
        String current =
                saved.getCardStatus() == null || saved.getCardStatus().isBlank()
                        ? "ACTIVE"
                        : saved.getCardStatus().trim().toUpperCase();
        if (prev.equals(current)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (!"ACTIVE".equals(current)) {
            notificationService.createOnce(
                    saved.getUserId(),
                    "CIRCULATION_LOCKED",
                    "Tài khoản/thẻ thư viện của bạn đã bị khóa (trạng thái: " + current + ").",
                    "USER",
                    saved.getUserId().toString(),
                    "ACCOUNT_LOCKED:" + saved.getUserId() + ":" + current + ":" + now.toLocalDate(),
                    now);
        } else {
            notificationService.createOnce(
                    saved.getUserId(),
                    "CIRCULATION_UNLOCKED",
                    "Tài khoản/thẻ thư viện của bạn đã được mở lại và hoạt động bình thường.",
                    "USER",
                    saved.getUserId().toString(),
                    "ACCOUNT_UNLOCKED:" + saved.getUserId() + ":ACTIVE:" + now.toLocalDate(),
                    now);
        }
    }

    @Transactional
    public void deleteStudent(String mssv) {
        UserEntity existing = getStudentByMssv(mssv);
        try {
            userRepository.delete(existing);
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Không thể xóa bạn đọc đã có lịch sử nghiệp vụ; hãy khóa thẻ thay vì xóa",
                    exception);
        }
    }

    @Transactional
    public void syncWithUniversity(List<UserEntity> universityData) {
        for (UserEntity uniUser : universityData) {
            if (uniUser.getMssv() == null || uniUser.getMssv().isEmpty()) continue;

            java.util.Optional<UserEntity> existingOpt =
                    userRepository.findByMssv(uniUser.getMssv());
            if (existingOpt.isPresent()) {
                // Update existing
                UserEntity existing = existingOpt.get();
                existing.setFullName(uniUser.getFullName());
                existing.setDepartment(uniUser.getDepartment());
                existing.setEmail(uniUser.getEmail());
                existing.setPhone(uniUser.getPhone());
                userRepository.save(existing);
                ensureWallet(existing);
            } else {
                createStudent(
                        new StudentUpsertRequestDTO(
                                uniUser.getMssv(),
                                uniUser.getFullName(),
                                uniUser.getEmail(),
                                uniUser.getDepartment(),
                                uniUser.getPhone(),
                                null,
                                null));
            }
        }
    }

    private void ensureWallet(UserEntity student) {
        if (student.getUserId() == null
                || walletRepository.findByUserId(student.getUserId()).isPresent()) {
            return;
        }
        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(student.getUserId());
        wallet.setDepositBalance(defaultDeposit.max(BigDecimal.ZERO));
        wallet.setTotalDebt(BigDecimal.ZERO);
        walletRepository.save(wallet);
    }

    private void applyProfile(UserEntity student, StudentUpsertRequestDTO request) {
        student.setMssv(request.mssv().trim());
        student.setFullName(request.fullName().trim());
        student.setEmail(request.email().trim().toLowerCase());
        student.setDepartment(trimToNull(request.department()));
        student.setPhone(trimToNull(request.phone()));
    }

    private void requireNoCardChangesBeforeOrientation(StudentUpsertRequestDTO request) {
        if (request.cardStatus() != null || request.cardExpiryDate() != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Thẻ chỉ được cấp khi sinh viên được điểm danh Có mặt tại lớp hướng dẫn thư viện");
        }
    }

    private void ensureUnique(String mssv, String email, UserEntity current) {
        userRepository
                .findByMssv(mssv.trim())
                .filter(user -> current == null || !user.getUserId().equals(current.getUserId()))
                .ifPresent(
                        user -> {
                            throw new ResponseStatusException(
                                    HttpStatus.CONFLICT, "MSSV đã tồn tại");
                        });
        userRepository
                .findByEmail(email.trim().toLowerCase())
                .filter(user -> current == null || !user.getUserId().equals(current.getUserId()))
                .ifPresent(
                        user -> {
                            throw new ResponseStatusException(
                                    HttpStatus.CONFLICT, "Email đã tồn tại");
                        });
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
