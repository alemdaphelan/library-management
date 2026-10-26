package com.huit.library.modules.orientation.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.orientation.dto.OrientationDtos;
import com.huit.library.modules.orientation.entity.OrientationClassEntity;
import com.huit.library.modules.orientation.entity.OrientationEnrollmentEntity;
import com.huit.library.modules.orientation.repository.OrientationClassRepository;
import com.huit.library.modules.orientation.repository.OrientationEnrollmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrientationServiceTest {
    private OrientationClassRepository classRepository;
    private OrientationEnrollmentRepository enrollmentRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;
    private OrientationService service;

    @BeforeEach
    void setUp() {
        classRepository = mock(OrientationClassRepository.class);
        enrollmentRepository = mock(OrientationEnrollmentRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        service = new OrientationService(classRepository, enrollmentRepository, userRepository, notificationService);
    }

    @Test
    void createsAClassWithThirtyStudentCapacityAndWeeklyCalendarData() {
        UUID actorId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.of(2026, 10, 27, 8, 0);
        when(classRepository.save(any())).thenAnswer(invocation -> {
            OrientationClassEntity entity = invocation.getArgument(0);
            entity.setClassId(11L);
            return entity;
        });
        when(enrollmentRepository.findByClassIdOrderByEnrollmentIdAsc(11L)).thenReturn(List.of());

        var result = service.create(new OrientationDtos.ClassRequest(
                "Hướng dẫn sử dụng thư viện", "Phòng A1", start, start.plusHours(2), 30, null), actorId);

        assertThat(result.classId()).isEqualTo(11L);
        assertThat(result.capacity()).isEqualTo(30);
        assertThat(result.status()).isEqualTo("SCHEDULED");
        assertThat(result.classCode()).startsWith("HD-");
    }

    @Test
    void assignsOnlyEligibleStudentsAndSendsARealAccountNotification() {
        UUID studentId = UUID.randomUUID();
        OrientationClassEntity orientationClass = scheduledClass();
        UserEntity student = new UserEntity();
        student.setUserId(studentId);
        student.setUserType("STUDENT");
        student.setMssv("2001231849");
        student.setFullName("Nguyễn Văn Sinh Viên");
        when(classRepository.findById(1L)).thenReturn(Optional.of(orientationClass));
        when(enrollmentRepository.countByClassId(1L)).thenReturn(0L);
        when(enrollmentRepository.findByClassIdAndUserId(1L, studentId)).thenReturn(Optional.empty());
        when(enrollmentRepository.findByClassIdOrderByEnrollmentIdAsc(1L)).thenReturn(List.of());
        when(enrollmentRepository.existsByUserIdAndAttendanceStatus(studentId, "ABSENT")).thenReturn(false);
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));

        service.assign(1L, new OrientationDtos.AssignmentRequest(List.of(studentId), 0));

        verify(enrollmentRepository).save(argThat(item -> studentId.equals(item.getUserId())
                && "ASSIGNED".equals(item.getAttendanceStatus())));
        verify(notificationService).createOnce(eq(studentId), eq("ORIENTATION_ASSIGNED"),
                contains("HD-TEST"), eq("ORIENTATION_CLASS"), eq("1"),
                eq("ORIENTATION_ASSIGNED:1:" + studentId), any(LocalDateTime.class));
    }

    @Test
    void rejectsManualAssignmentForAStudentWhoAlreadyHasALibraryCard() {
        UUID studentId = UUID.randomUUID();
        UserEntity student = new UserEntity();
        student.setUserId(studentId);
        student.setUserType("STUDENT");
        student.setCardStatus("ACTIVE");
        when(classRepository.findById(1L)).thenReturn(Optional.of(scheduledClass()));
        when(enrollmentRepository.findByClassIdAndUserId(1L, studentId)).thenReturn(Optional.empty());
        when(userRepository.findById(studentId)).thenReturn(Optional.of(student));

        assertThatThrownBy(() -> service.assign(1L,
                new OrientationDtos.AssignmentRequest(List.of(studentId), 0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("chưa được cấp thẻ");
    }

    private OrientationClassEntity scheduledClass() {
        OrientationClassEntity entity = new OrientationClassEntity();
        entity.setClassId(1L);
        entity.setClassCode("HD-TEST");
        entity.setTitle("Hướng dẫn sử dụng thư viện");
        entity.setLocation("Phòng A1");
        entity.setStartAt(LocalDateTime.of(2026, 10, 27, 8, 0));
        entity.setEndAt(LocalDateTime.of(2026, 10, 27, 10, 0));
        entity.setCapacity(30);
        entity.setStatus("SCHEDULED");
        return entity;
    }
}
