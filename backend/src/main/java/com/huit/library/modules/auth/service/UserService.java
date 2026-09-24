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

    public UserEntity getStudentById(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new RuntimeException("Student not found"));
    }

    public UserEntity createStudent(UserEntity student) {
        student.setUserType("STUDENT");
        student.setRoleId("STUDENT");
        student.setPasswordHash(passwordEncoder.encode(student.getStudentId())); // default password is student ID
        student.setIsFirstLogin(true);
        return userRepository.save(student);
    }

    public UserEntity updateStudent(UUID id, UserEntity updatedStudent) {
        UserEntity existing = getStudentById(id);
        existing.setFullName(updatedStudent.getFullName());
        existing.setDepartment(updatedStudent.getDepartment());
        existing.setEmail(updatedStudent.getEmail());
        existing.setPhone(updatedStudent.getPhone());
        return userRepository.save(existing);
    }

    public void deleteStudent(UUID id) {
        userRepository.deleteById(id);
    }
}
