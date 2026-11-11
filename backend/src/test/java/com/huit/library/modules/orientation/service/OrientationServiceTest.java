package com.huit.library.modules.orientation.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.orientation.dto.OrientationDtos;
import com.huit.library.modules.orientation.entity.OrientationClassEntity;
import com.huit.library.modules.orientation.entity.OrientationEnrollmentEntity;
import com.huit.library.modules.orientation.repository.OrientationClassRepository;
import com.huit.library.modules.orientation.repository.OrientationEnrollmentRepository;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
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
        service =
                new OrientationService(
                        classRepository, enrollmentRepository, userRepository, notificationService);
    }

    @Test
    void createsAClassWithThirtyStudentCapacityAndWeeklyCalendarData() {
        UUID actorId = UUID.randomUUID();
        LocalDateTime start =
                LocalDateTime.now().plusDays(7).withHour(8).withMinute(0).withSecond(0).withNano(0);
        when(classRepository.save(any()))
                .thenAnswer(
                        invocation -> {
                            OrientationClassEntity entity = invocation.getArgument(0);
                            entity.setClassId(11L);
                            return entity;
                        });
        when(enrollmentRepository.findByClassIdOrderByEnrollmentIdAsc(11L)).thenReturn(List.of());

        var result =
                service.create(
                        new OrientationDtos.ClassRequest(
                                "Hướng dẫn sử dụng thư viện",
                                "Phòng A1",
                                start,
                                start.plusHours(2),
                                30,
                                null),
                        actorId);

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
        when(classRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(orientationClass));
        when(enrollmentRepository.countByClassId(1L)).thenReturn(0L);
        when(enrollmentRepository.findByClassIdAndUserId(1L, studentId))
                .thenReturn(Optional.empty());
        when(enrollmentRepository.findByClassIdOrderByEnrollmentIdAsc(1L)).thenReturn(List.of());
        when(enrollmentRepository.existsByUserIdAndAttendanceStatus(studentId, "ABSENT"))
                .thenReturn(false);
        when(userRepository.findByIdForUpdate(studentId)).thenReturn(Optional.of(student));

        service.assign(1L, new OrientationDtos.AssignmentRequest(List.of(studentId), 0));

        verify(enrollmentRepository)
                .save(
                        argThat(
                                item ->
                                        studentId.equals(item.getUserId())
                                                && "ASSIGNED".equals(item.getAttendanceStatus())));
        verify(notificationService)
                .createOnce(
                        eq(studentId),
                        eq("ORIENTATION_ASSIGNED"),
                        contains("HD-TEST"),
                        eq("ORIENTATION_CLASS"),
                        eq("1"),
                        eq("ORIENTATION_ASSIGNED:1:" + studentId),
                        any(LocalDateTime.class));
    }

    @Test
    void rejectsManualAssignmentForAStudentWhoAlreadyHasALibraryCard() {
        UUID studentId = UUID.randomUUID();
        UserEntity student = new UserEntity();
        student.setUserId(studentId);
        student.setUserType("STUDENT");
        student.setCardStatus("ACTIVE");
        when(classRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(scheduledClass()));
        when(enrollmentRepository.findByClassIdAndUserId(1L, studentId))
                .thenReturn(Optional.empty());
        when(userRepository.findByIdForUpdate(studentId)).thenReturn(Optional.of(student));

        assertThatThrownBy(
                        () ->
                                service.assign(
                                        1L,
                                        new OrientationDtos.AssignmentRequest(
                                                List.of(studentId), 0)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("chưa được cấp thẻ");
    }

    @Test
    void removesAnAccidentallyAssignedStudentFromAClassThatHasNotStarted() {
        OrientationClassEntity orientationClass = scheduledClass();
        OrientationEnrollmentEntity enrollment = new OrientationEnrollmentEntity();
        enrollment.setEnrollmentId(15L);
        enrollment.setClassId(1L);
        when(classRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(orientationClass));
        when(enrollmentRepository.findByEnrollmentIdAndClassId(15L, 1L))
                .thenReturn(Optional.of(enrollment));

        service.unassign(1L, 15L);

        verify(enrollmentRepository).delete(enrollment);
    }

    @Test
    void refusesToRemoveAttendanceHistoryAfterAClassHasStarted() {
        OrientationClassEntity orientationClass = scheduledClass();
        orientationClass.setStartAt(LocalDateTime.now().minusMinutes(1));
        when(classRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(orientationClass));

        assertThatThrownBy(() -> service.unassign(1L, 15L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("chưa bắt đầu");
        verify(enrollmentRepository, never()).delete(any());
    }

    @Test
    void issuesCardOnceForAttendanceAndKeepsLaterStaffLocks() {
        var student = enrolledStudentInStartedClass();
        UUID actor = UUID.randomUUID();
        var present = attendance(student, "ATTENDED");

        var result = service.markAttendance(1L, present, actor);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.enrollments().get(0).cardStatus()).isEqualTo("ACTIVE");
        var issuedAt = student.getCardIssuedAt();
        assertThat(issuedAt).isNotNull();
        assertThat(student.getCardExpiryDate()).isEqualTo(issuedAt.plusYears(1));
        student.setCardStatus("LOCKED");
        service.markAttendance(1L, present, actor);
        assertThat(student.getCardStatus()).isEqualTo("LOCKED");
        assertThat(student.getCardIssuedAt()).isEqualTo(issuedAt);
        assertThat(student.getCardExpiryDate()).isEqualTo(issuedAt.plusYears(1));
        verify(notificationService)
                .createOnce(
                        eq(student.getUserId()),
                        eq("ORIENTATION_CARD_ACTIVATED"),
                        contains("HD-TEST"),
                        eq("ORIENTATION_CLASS"),
                        eq("1"),
                        anyString(),
                        eq(issuedAt));
    }

    @Test
    void rejectsEarlyOrDuplicateAttendanceAndDoesNotIssueForAbsence() {
        var student = enrolledStudentInStartedClass();
        UUID actor = UUID.randomUUID();
        var scheduled = scheduledClass();
        when(classRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(scheduled));
        assertThatThrownBy(() -> service.markAttendance(1L, attendance(student, "ATTENDED"), actor))
                .hasMessageContaining("sau khi lớp bắt đầu");

        scheduled.setStartAt(LocalDateTime.now().minusHours(2));
        var item = attendance(student, "ATTENDED").items().get(0);
        assertThatThrownBy(
                        () ->
                                service.markAttendance(
                                        1L,
                                        new OrientationDtos.AttendanceRequest(List.of(item, item)),
                                        actor))
                .hasMessageContaining("chỉ xuất hiện một lần");
        service.markAttendance(1L, attendance(student, "EXCUSED"), actor);
        service.markAttendance(1L, attendance(student, "ABSENT"), actor);
        assertThat(student.hasLibraryCard()).isFalse();
        verifyNoInteractions(notificationService);
        verify(userRepository, never()).save(any());
    }

    @Test
    void excelOnlyIssuesForAnExplicitPresenceMark() throws Exception {
        var student = enrolledStudentInStartedClass();
        when(userRepository.findUserIdByMssv(student.getMssv()))
                .thenReturn(Optional.of(student.getUserId()));
        UUID actor = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                service.importAttendance(
                                        1L, excel(student.getMssv(), "Không"), actor))
                .hasMessageContaining("đánh dấu x");
        assertThat(student.hasLibraryCard()).isFalse();

        service.importAttendance(1L, excel(student.getMssv(), "X"), actor);
        assertThat(student.getCardStatus()).isEqualTo("ACTIVE");
    }

    private UserEntity enrolledStudentInStartedClass() {
        var orientationClass = scheduledClass();
        orientationClass.setStartAt(LocalDateTime.now().minusHours(2));
        when(classRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(orientationClass));
        var student = new UserEntity();
        student.setUserId(UUID.randomUUID());
        student.setMssv("2001234567");
        student.setUserType("STUDENT");
        var enrollment = new OrientationEnrollmentEntity();
        enrollment.setUserId(student.getUserId());
        enrollment.setAttendanceStatus("ASSIGNED");
        when(enrollmentRepository.findByClassIdAndUserId(1L, student.getUserId()))
                .thenReturn(Optional.of(enrollment));
        when(enrollmentRepository.findByClassIdOrderByEnrollmentIdAsc(1L))
                .thenReturn(List.of(enrollment));
        when(userRepository.findByIdForUpdate(student.getUserId()))
                .thenReturn(Optional.of(student));
        when(userRepository.findById(student.getUserId())).thenReturn(Optional.of(student));
        return student;
    }

    private OrientationDtos.AttendanceRequest attendance(UserEntity student, String status) {
        return new OrientationDtos.AttendanceRequest(
                List.of(new OrientationDtos.AttendanceItem(student.getUserId(), status, null)));
    }

    private MockMultipartFile excel(String mssv, String mark) throws Exception {
        try (var workbook = new XSSFWorkbook();
                var output = new ByteArrayOutputStream()) {
            var row = workbook.createSheet().createRow(3);
            row.createCell(0).setCellValue(mssv);
            row.createCell(2).setCellValue(mark);
            workbook.write(output);
            return new MockMultipartFile(
                    "file", "attendance.xlsx", "application/octet-stream", output.toByteArray());
        }
    }

    private OrientationClassEntity scheduledClass() {
        OrientationClassEntity entity = new OrientationClassEntity();
        entity.setClassId(1L);
        entity.setClassCode("HD-TEST");
        entity.setTitle("Hướng dẫn sử dụng thư viện");
        entity.setLocation("Phòng A1");
        entity.setStartAt(
                LocalDateTime.now()
                        .plusDays(7)
                        .withHour(8)
                        .withMinute(0)
                        .withSecond(0)
                        .withNano(0));
        entity.setEndAt(entity.getStartAt().plusHours(2));
        entity.setCapacity(30);
        entity.setStatus("SCHEDULED");
        return entity;
    }
}
