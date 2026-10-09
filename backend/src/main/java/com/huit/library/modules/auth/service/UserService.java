package com.huit.library.modules.auth.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserEntity> getAllStudents() {
        return userRepository.findAll().stream()
                .filter(u -> "STUDENT".equals(u.getUserType()))
                .collect(Collectors.toList());
    }

    public UserEntity getStudentByMssv(String mssv) {
        return userRepository.findByMssv(mssv).orElseThrow(() -> new RuntimeException("Student not found"));
    }

    public UserEntity createStudent(UserEntity student) {
        student.setUserType("STUDENT");
        student.setRoleId("STUDENT");
        student.setPasswordHash(passwordEncoder.encode(student.getMssv())); // default password is mssv
        student.setIsFirstLogin(true);
        return userRepository.save(student);
    }

    public UserEntity updateStudent(String mssv, UserEntity updatedStudent) {
        UserEntity existing = getStudentByMssv(mssv);
        existing.setFullName(updatedStudent.getFullName());
        existing.setDepartment(updatedStudent.getDepartment());
        existing.setEmail(updatedStudent.getEmail());
        existing.setPhone(updatedStudent.getPhone());
        return userRepository.save(existing);
    }

    public void deleteStudent(String mssv) {
        UserEntity existing = getStudentByMssv(mssv);
        userRepository.deleteById(existing.getUserId());
    }


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
            } else {
                // Create new
                uniUser.setUserType("STUDENT");
                uniUser.setRoleId("STUDENT");
                uniUser.setPasswordHash(passwordEncoder.encode(uniUser.getMssv()));
                uniUser.setIsFirstLogin(true);
                userRepository.save(uniUser);
            }
        }
    }
}
