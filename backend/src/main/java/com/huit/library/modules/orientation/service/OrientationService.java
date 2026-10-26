package com.huit.library.modules.orientation.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.notification.service.NotificationService;
import com.huit.library.modules.orientation.dto.OrientationDtos;
import com.huit.library.modules.orientation.entity.OrientationClassEntity;
import com.huit.library.modules.orientation.entity.OrientationEnrollmentEntity;
import com.huit.library.modules.orientation.repository.OrientationClassRepository;
import com.huit.library.modules.orientation.repository.OrientationEnrollmentRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class OrientationService {
    private static final Set<String> ATTENDANCE = Set.of("ASSIGNED", "ATTENDED", "ABSENT", "EXCUSED");
    private final OrientationClassRepository classRepository;
    private final OrientationEnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public OrientationService(OrientationClassRepository classRepository,
                              OrientationEnrollmentRepository enrollmentRepository,
                              UserRepository userRepository,
                              NotificationService notificationService) {
        this.classRepository = classRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<OrientationDtos.ClassResponse> list() {
        return classRepository.findAllByOrderByStartAtAsc().stream().map(this::response).toList();
    }

    @Transactional
    public OrientationDtos.ClassResponse create(OrientationDtos.ClassRequest request, UUID actorId) {
        validateTimes(request.startAt(), request.endAt());
        validateFacilitator(request.facilitatorId(), request.startAt(), request.endAt());
        OrientationClassEntity entity = new OrientationClassEntity();
        entity.setClassCode("HD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        apply(entity, request);
        entity.setCreatedBy(actorId);
        entity.setStatus("SCHEDULED");
        entity = classRepository.save(entity);
        return response(entity);
    }

    @Transactional
    public OrientationDtos.ClassResponse update(Long id, OrientationDtos.ClassRequest request) {
        validateTimes(request.startAt(), request.endAt());
        OrientationClassEntity entity = find(id);
        if (request.facilitatorId() != null
                && classRepository.existsByFacilitatorIdAndClassIdNotAndStartAtLessThanAndEndAtGreaterThan(
                request.facilitatorId(), id, request.endAt(), request.startAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Người phụ trách đã có lớp trùng thời gian");
        }
        apply(entity, request);
        return response(classRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        OrientationClassEntity entity = find(id);
        if (entity.getStartAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xóa lớp đã bắt đầu; hãy lưu làm lịch sử");
        }
        classRepository.delete(entity);
    }

    @Transactional(readOnly = true)
    public List<OrientationDtos.Person> availableStudents(Long classId) {
        find(classId);
        return userRepository.findByUserTypeOrderByFullNameAsc("STUDENT").stream()
                .filter(user -> user.getCardStatus() == null || user.getCardStatus().isBlank())
                .filter(user -> enrollmentRepository.findByClassIdAndUserId(classId, user.getUserId()).isEmpty())
                .filter(user -> !enrollmentRepository.existsByUserIdAndAttendanceStatus(user.getUserId(), "ABSENT"))
                .map(this::person).toList();
    }

    @Transactional(readOnly = true)
    public List<OrientationDtos.Person> availableFacilitators(LocalDateTime startAt, LocalDateTime endAt) {
        validateTimes(startAt, endAt);
        return userRepository.findStaffByRoleIds(List.of("ADMIN", "LIBRARIAN")).stream()
                .filter(user -> !classRepository.existsByFacilitatorIdAndStartAtLessThanAndEndAtGreaterThan(
                        user.getUserId(), endAt, startAt))
                .map(this::person).toList();
    }

    @Transactional
    public OrientationDtos.ClassResponse assign(Long classId, OrientationDtos.AssignmentRequest request) {
        OrientationClassEntity orientationClass = find(classId);
        List<UUID> selected = new ArrayList<>(request.userIds() == null ? List.of() : request.userIds());
        int randomCount = request.randomCount() == null ? 0 : request.randomCount();
        if (randomCount > 0) {
            List<UUID> candidates = new ArrayList<>(availableStudents(classId).stream()
                    .map(OrientationDtos.Person::userId).filter(id -> !selected.contains(id)).toList());
            Collections.shuffle(candidates);
            selected.addAll(candidates.stream().limit(randomCount).toList());
        }
        long current = enrollmentRepository.countByClassId(classId);
        long newCount = selected.stream().distinct()
                .filter(userId -> enrollmentRepository.findByClassIdAndUserId(classId, userId).isEmpty()).count();
        if (current + newCount > orientationClass.getCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sĩ số lớp tối đa là " + orientationClass.getCapacity());
        }
        for (UUID userId : selected.stream().distinct().toList()) {
            UserEntity student = userRepository.findById(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sinh viên"));
            if (!"STUDENT".equalsIgnoreCase(student.getUserType())) continue;
            if (student.getCardStatus() != null && !student.getCardStatus().isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Chỉ sinh viên chưa được cấp thẻ mới thuộc diện xếp lớp hướng dẫn");
            }
            if (enrollmentRepository.existsByUserIdAndAttendanceStatus(userId, "ABSENT")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Sinh viên có lịch sử vắng không phép; Admin cần xác minh trước khi xếp lại");
            }
            if (enrollmentRepository.findByClassIdAndUserId(classId, userId).isPresent()) continue;
            OrientationEnrollmentEntity enrollment = new OrientationEnrollmentEntity();
            enrollment.setClassId(classId);
            enrollment.setUserId(userId);
            enrollment.setAttendanceStatus("ASSIGNED");
            enrollmentRepository.save(enrollment);
            notificationService.createOnce(userId, "ORIENTATION_ASSIGNED",
                    "Bạn được xếp vào lớp hướng dẫn sử dụng thư viện " + orientationClass.getClassCode()
                            + " lúc " + orientationClass.getStartAt().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"))
                            + " tại " + orientationClass.getLocation() + ".",
                    "ORIENTATION_CLASS", classId.toString(),
                    "ORIENTATION_ASSIGNED:" + classId + ":" + userId, LocalDateTime.now());
        }
        return response(orientationClass);
    }

    @Transactional
    public OrientationDtos.ClassResponse markAttendance(Long classId, OrientationDtos.AttendanceRequest request, UUID actorId) {
        OrientationClassEntity orientationClass = find(classId);
        for (OrientationDtos.AttendanceItem item : request.items()) {
            String status = item.status().trim().toUpperCase(Locale.ROOT);
            if (!ATTENDANCE.contains(status) || "ASSIGNED".equals(status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái điểm danh không hợp lệ");
            }
            OrientationEnrollmentEntity enrollment = enrollmentRepository.findByClassIdAndUserId(classId, item.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sinh viên chưa thuộc lớp"));
            enrollment.setAttendanceStatus(status);
            enrollment.setNote(item.note());
            enrollment.setMarkedBy(actorId);
            enrollment.setMarkedAt(LocalDateTime.now());
            enrollmentRepository.save(enrollment);
        }
        if (enrollmentRepository.findByClassIdOrderByEnrollmentIdAsc(classId).stream()
                .noneMatch(enrollment -> "ASSIGNED".equals(enrollment.getAttendanceStatus()))) {
            orientationClass.setStatus("COMPLETED");
            classRepository.save(orientationClass);
        }
        return response(orientationClass);
    }

    @Transactional(readOnly = true)
    public byte[] attendanceTemplate(Long classId) {
        OrientationClassEntity orientationClass = find(classId);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Diem danh");
            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("Lớp"); title.createCell(1).setCellValue(orientationClass.getClassCode());
            Row header = sheet.createRow(2);
            String[] headers = {"MSSV", "Họ tên", "Có mặt (x)", "Vắng có phép (x)", "Ghi chú"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            int rowIndex = 3;
            for (OrientationEnrollmentEntity enrollment : enrollmentRepository.findByClassIdOrderByEnrollmentIdAsc(classId)) {
                UserEntity user = userRepository.findById(enrollment.getUserId()).orElse(null);
                if (user == null) continue;
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(user.getMssv());
                row.createCell(1).setCellValue(user.getFullName());
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo tệp điểm danh", exception);
        }
    }

    @Transactional
    public OrientationDtos.ClassResponse importAttendance(Long classId, MultipartFile file, UUID actorId) {
        find(classId);
        List<OrientationDtos.AttendanceItem> items = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int index = 3; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                String mssv = cell(row, 0);
                if (mssv.isBlank()) continue;
                UserEntity user = userRepository.findByMssv(mssv)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy MSSV " + mssv));
                String status = !cell(row, 2).isBlank() ? "ATTENDED" : (!cell(row, 3).isBlank() ? "EXCUSED" : "ABSENT");
                items.add(new OrientationDtos.AttendanceItem(user.getUserId(), status, cell(row, 4)));
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tệp Excel điểm danh không hợp lệ", exception);
        }
        return markAttendance(classId, new OrientationDtos.AttendanceRequest(items), actorId);
    }

    private OrientationDtos.ClassResponse response(OrientationClassEntity entity) {
        UserEntity facilitator = entity.getFacilitatorId() == null ? null : userRepository.findById(entity.getFacilitatorId()).orElse(null);
        List<OrientationDtos.Enrollment> enrollments = enrollmentRepository.findByClassIdOrderByEnrollmentIdAsc(entity.getClassId())
                .stream().map(enrollment -> new OrientationDtos.Enrollment(
                        enrollment.getEnrollmentId(), userRepository.findById(enrollment.getUserId()).map(this::person).orElse(null),
                        enrollment.getAttendanceStatus(), enrollment.getNote())).toList();
        return new OrientationDtos.ClassResponse(entity.getClassId(), entity.getClassCode(), entity.getTitle(), entity.getLocation(),
                entity.getStartAt(), entity.getEndAt(), entity.getCapacity(), entity.getFacilitatorId(),
                facilitator == null ? null : facilitator.getFullName(), entity.getStatus(), enrollments.size(), enrollments);
    }

    private void apply(OrientationClassEntity entity, OrientationDtos.ClassRequest request) {
        entity.setTitle(request.title().trim()); entity.setLocation(request.location().trim());
        entity.setStartAt(request.startAt()); entity.setEndAt(request.endAt());
        entity.setCapacity(request.capacity() == null ? 30 : request.capacity()); entity.setFacilitatorId(request.facilitatorId());
    }
    private void validateTimes(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !end.isAfter(start)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giờ kết thúc phải sau giờ bắt đầu");
    }
    private void validateFacilitator(UUID id, LocalDateTime start, LocalDateTime end) {
        if (id != null && classRepository.existsByFacilitatorIdAndStartAtLessThanAndEndAtGreaterThan(id, end, start))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Người phụ trách đã có lớp trùng thời gian");
    }
    private OrientationClassEntity find(Long id) { return classRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp hướng dẫn")); }
    private OrientationDtos.Person person(UserEntity user) { return new OrientationDtos.Person(user.getUserId(), user.getFullName(), user.getMssv(), user.getEmail()); }
    private String cell(Row row, int index) { Cell cell = row.getCell(index); return cell == null ? "" : new DataFormatter().formatCellValue(cell).trim(); }
}
