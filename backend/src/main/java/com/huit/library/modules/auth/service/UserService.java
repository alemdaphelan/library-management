package com.huit.library.modules.auth.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.dto.StudentUpsertRequestDTO;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.finance.entity.WalletEntity;
import com.huit.library.modules.finance.repository.WalletRepository;
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
    private final BigDecimal defaultDeposit;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            WalletRepository walletRepository,
            @org.springframework.beans.factory.annotation.Value(
                    "${library.circulation.default-security-deposit:50000}") BigDecimal defaultDeposit) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.walletRepository = walletRepository;
        this.defaultDeposit = defaultDeposit;
    }

    public List<UserEntity> getAllStudents() {
        return userRepository.findByUserTypeOrderByFullNameAsc("STUDENT");
    }

    public UserEntity getStudentByMssv(String mssv) {
        return userRepository.findByMssv(mssv).orElseThrow(() -> new RuntimeException("Student not found"));
    }

    @Transactional
    public UserEntity createStudent(StudentUpsertRequestDTO request) {
        ensureUnique(request.mssv(), request.email(), null);
        UserEntity student = new UserEntity();
        apply(student, request, true);
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
        UserEntity existing = getStudentByMssv(mssv);
        ensureUnique(request.mssv(), request.email(), existing);
        apply(existing, request, false);
        return userRepository.save(existing);
    }

    @Transactional
    public void deleteStudent(String mssv) {
        UserEntity existing = getStudentByMssv(mssv);
        try {
            userRepository.delete(existing);
            userRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Không thể xóa bạn đọc đã có lịch sử nghiệp vụ; hãy khóa thẻ thay vì xóa", exception);
        }
    }


    @Transactional
    public void syncWithUniversity(List<UserEntity> universityData) {
        for (UserEntity uniUser : universityData) {
            if (uniUser.getMssv() == null || uniUser.getMssv().isEmpty()) continue;
            
            java.util.Optional<UserEntity> existingOpt = userRepository.findByMssv(uniUser.getMssv());
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
                // Create new
                uniUser.setUserType("STUDENT");
                uniUser.setRoleId("STUDENT");
                uniUser.setPasswordHash(passwordEncoder.encode(uniUser.getMssv()));
                uniUser.setIsFirstLogin(true);
                uniUser = userRepository.save(uniUser);
                ensureWallet(uniUser);
            }
        }
    }

    private void ensureWallet(UserEntity student) {
        if (student.getUserId() == null || walletRepository.findByUserId(student.getUserId()).isPresent()) {
            return;
        }
        WalletEntity wallet = new WalletEntity();
        wallet.setUserId(student.getUserId());
        wallet.setDepositBalance(defaultDeposit.max(BigDecimal.ZERO));
        wallet.setTotalDebt(BigDecimal.ZERO);
        walletRepository.save(wallet);
    }

    private void apply(UserEntity student, StudentUpsertRequestDTO request, boolean creating) {
        student.setMssv(request.mssv().trim());
        student.setFullName(request.fullName().trim());
        student.setEmail(request.email().trim().toLowerCase());
        student.setDepartment(trimToNull(request.department()));
        student.setPhone(trimToNull(request.phone()));
        student.setCardStatus(request.cardStatus() == null ? "ACTIVE" : request.cardStatus());
        if (request.cardExpiryDate() != null) {
            student.setCardExpiryDate(request.cardExpiryDate());
        } else if (creating) {
            student.setCardExpiryDate(LocalDateTime.now().plusYears(1));
        }
    }

    private void ensureUnique(String mssv, String email, UserEntity current) {
        userRepository.findByMssv(mssv.trim())
                .filter(user -> current == null || !user.getUserId().equals(current.getUserId()))
                .ifPresent(user -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "MSSV đã tồn tại");
                });
        userRepository.findByEmail(email.trim().toLowerCase())
                .filter(user -> current == null || !user.getUserId().equals(current.getUserId()))
                .ifPresent(user -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email đã tồn tại");
                });
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
