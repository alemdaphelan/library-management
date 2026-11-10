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

    @Transactional(readOnly = true)
    public OrientationDtos.ClassResponse get(Long id) {
        return response(find(id));
    }

    @Transactional
    public OrientationDtos.ClassResponse create(OrientationDtos.ClassRequest request, UUID actorId) {
        validateTimes(request.startAt(), request.endAt());
        if (!request.startAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lớp hướng dẫn phải bắt đầu trong tương lai");
        }
        UUID facilitatorId = request.facilitatorId() == null ? actorId : request.facilitatorId();
        validateFacilitator(facilitatorId, request.startAt(), request.endAt());
        OrientationClassEntity entity = new OrientationClassEntity();
        entity.setClassCode("HD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT));
        apply(entity, request);
        entity.setFacilitatorId(facilitatorId);
        entity.setCreatedBy(actorId);
        entity.setStatus("SCHEDULED");
        entity = classRepository.save(entity);
        return response(entity);
    }

    @Transactional
    public OrientationDtos.ClassResponse update(Long id, OrientationDtos.ClassRequest request) {
        validateTimes(request.startAt(), request.endAt());
        OrientationClassEntity entity = findForUpdate(id);
        if (!"SCHEDULED".equals(entity.getStatus()) || !entity.getStartAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể sửa lớp đã bắt đầu hoặc hoàn tất");
        }
        UUID existingFacilitatorId = entity.getFacilitatorId();
        if (request.facilitatorId() != null
                && classRepository.existsByFacilitatorIdAndClassIdNotAndStartAtLessThanAndEndAtGreaterThan(
                request.facilitatorId(), id, request.endAt(), request.startAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Người phụ trách đã có lớp trùng thời gian");
        }
        apply(entity, request);
        if (request.facilitatorId() == null) entity.setFacilitatorId(existingFacilitatorId);
        return response(classRepository.save(entity));
    }

    @Transactional
    public void delete(Long id) {
        OrientationClassEntity entity = findForUpdate(id);
        if (entity.getStartAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không thể xóa lớp đã bắt đầu; hãy lưu làm lịch sử");
        }
        classRepository.delete(entity);
    }

    @Transactional
    public void unassign(Long classId, Long enrollmentId) {
        OrientationClassEntity orientationClass = findForUpdate(classId);
        if (!"SCHEDULED".equals(orientationClass.getStatus())
                || !orientationClass.getStartAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ có thể gỡ sinh viên khỏi lớp chưa bắt đầu");
        }
        OrientationEnrollmentEntity enrollment = enrollmentRepository
                .findByEnrollmentIdAndClassId(enrollmentId, classId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy sinh viên trong lớp hướng dẫn"));
        enrollmentRepository.delete(enrollment);
    }

    @Transactional(readOnly = true)
    public List<OrientationDtos.Person> availableStudents(Long classId) {
        find(classId);
        return enrollmentRepository.findAvailableStudents(classId).stream()
                .map(user -> new OrientationDtos.Person(user.getUserId(), user.getFullName(), user.getMssv(), user.getEmail()))
                .toList();
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
        OrientationClassEntity orientationClass = findForUpdate(classId);
        if (!"SCHEDULED".equals(orientationClass.getStatus())
                || !orientationClass.getStartAt().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể xếp sinh viên vào lớp chưa bắt đầu");
        }
        List<UUID> selected = new ArrayList<>(request.userIds() == null ? List.of() : request.userIds());
        int randomCount = request.randomCount() == null ? 0 : request.randomCount();
        if (randomCount > 0) {
            List<UUID> candidates = new ArrayList<>(enrollmentRepository.findAvailableStudents(classId).stream()
                    .map(OrientationEnrollmentRepository.StudentCandidate::getUserId)
                    .filter(id -> !selected.contains(id)).toList());
            Collections.shuffle(candidates);
            selected.addAll(candidates.stream().limit(randomCount).toList());
        }
        long current = enrollmentRepository.countByClassId(classId);
        long newCount = selected.stream().distinct()
                .filter(userId -> enrollmentRepository.findByClassIdAndUserId(classId, userId).isEmpty()).count();
        if (current + newCount > orientationClass.getCapacity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sĩ số lớp tối đa là " + orientationClass.getCapacity());
        }
        for (UUID userId : selected.stream().distinct().sorted().toList()) {
            if (enrollmentRepository.findByClassIdAndUserId(classId, userId).isPresent()) continue;
            UserEntity student = userRepository.findByIdForUpdate(userId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sinh viên"));
            if (!"STUDENT".equalsIgnoreCase(student.getUserType())) continue;
            if (student.hasLibraryCard()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Chỉ sinh viên chưa được cấp thẻ mới thuộc diện xếp lớp hướng dẫn");
            }
            if (enrollmentRepository.existsByUserIdAndAttendanceStatus(userId, "ABSENT")) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Sinh viên có lịch sử vắng không phép; Admin cần xác minh trước khi xếp lại");
            }
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
        OrientationClassEntity orientationClass = findForUpdate(classId);
        LocalDateTime now = LocalDateTime.now();
        if (orientationClass.getStartAt().isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ có thể điểm danh sau khi lớp bắt đầu");
        }
        List<OrientationDtos.AttendanceItem> items = request.items();
        if (items.isEmpty() || items.stream().map(OrientationDtos.AttendanceItem::userId).distinct().count() != items.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Danh sách điểm danh phải có dữ liệu và mỗi sinh viên chỉ xuất hiện một lần");
        }
        for (OrientationDtos.AttendanceItem item : items.stream()
                .sorted(Comparator.comparing(OrientationDtos.AttendanceItem::userId)).toList()) {
            String status = item.status().trim().toUpperCase(Locale.ROOT);
            if (!ATTENDANCE.contains(status) || "ASSIGNED".equals(status)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Trạng thái điểm danh không hợp lệ");
            }
            OrientationEnrollmentEntity enrollment = enrollmentRepository.findByClassIdAndUserId(classId, item.userId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Sinh viên chưa thuộc lớp"));
            enrollment.setAttendanceStatus(status);
            enrollment.setNote(item.note());
            enrollment.setMarkedBy(actorId);
            enrollment.setMarkedAt(now);
            enrollmentRepository.save(enrollment);
            if ("ATTENDED".equals(status)) {
                issueLibraryCard(orientationClass, item.userId(), now);
            }
        }
        if (enrollmentRepository.findByClassIdOrderByEnrollmentIdAsc(classId).stream()
                .noneMatch(enrollment -> "ASSIGNED".equals(enrollment.getAttendanceStatus()))) {
            orientationClass.setStatus("COMPLETED");
            classRepository.save(orientationClass);
        }
        return response(orientationClass);
    }

    private void issueLibraryCard(OrientationClassEntity orientationClass, UUID userId, LocalDateTime now) {
        UserEntity student = userRepository.findByIdForUpdate(userId)
                .filter(user -> "STUDENT".equalsIgnoreCase(user.getUserType()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy sinh viên"));
        if (student.hasLibraryCard()) {
            return;
        }
        student.setCardStatus("ACTIVE");
        student.setCardIssuedAt(now);
        student.setCardExpiryDate(now.plusYears(1));
        userRepository.save(student);
        notificationService.createOnce(userId, "ORIENTATION_CARD_ACTIVATED",
                "Bạn đã hoàn thành lớp hướng dẫn " + orientationClass.getClassCode()
                        + ". Thẻ thư viện đã được kích hoạt và có hiệu lực đến "
                        + student.getCardExpiryDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".",
                "ORIENTATION_CLASS", orientationClass.getClassId().toString(),
                "ORIENTATION_CARD_ACTIVATED:" + userId, now);
    }

    @Transactional(readOnly = true)
    public byte[] attendanceTemplate(Long classId) {
        OrientationClassEntity orientationClass = find(classId);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Diem danh");
            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("Lớp"); title.createCell(1).setCellValue(orientationClass.getClassCode());
            sheet.createRow(1).createCell(0).setCellValue("Đánh dấu x vào một cột. Bỏ trống cả hai cột để ghi vắng không phép.");
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
                if ("ATTENDED".equals(enrollment.getAttendanceStatus())) row.createCell(2).setCellValue("x");
                if ("EXCUSED".equals(enrollment.getAttendanceStatus())) row.createCell(3).setCellValue("x");
                if (enrollment.getNote() != null) row.createCell(4).setCellValue(enrollment.getNote());
            }
            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
            sheet.setColumnWidth(0, 18 * 256);
            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Không thể tạo tệp điểm danh", exception);
        }
    }

    @Transactional
    public OrientationDtos.ClassResponse importAttendance(Long classId, MultipartFile file, UUID actorId) {
        List<OrientationDtos.AttendanceItem> items = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int index = 3; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                if (row == null) continue;
                String mssv = cell(row, 0);
                if (mssv.isBlank()) continue;
                UUID userId = userRepository.findUserIdByMssv(mssv)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy MSSV " + mssv));
                String present = cell(row, 2);
                String excused = cell(row, 3);
                if ((!present.isBlank() && !"x".equalsIgnoreCase(present))
                        || (!excused.isBlank() && !"x".equalsIgnoreCase(excused))
                        || (!present.isBlank() && !excused.isBlank())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Chỉ đánh dấu x ở một cột điểm danh tại dòng " + (index + 1));
                }
                String status = !present.isBlank() ? "ATTENDED" : (!excused.isBlank() ? "EXCUSED" : "ABSENT");
                items.add(new OrientationDtos.AttendanceItem(userId, status, cell(row, 4)));
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
                .stream().map(enrollment -> {
                    UserEntity student = userRepository.findById(enrollment.getUserId()).orElse(null);
                    return new OrientationDtos.Enrollment(enrollment.getEnrollmentId(),
                            student == null ? null : person(student), enrollment.getAttendanceStatus(), enrollment.getNote(),
                            student == null ? null : student.getCardStatus());
                }).toList();
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
    private OrientationClassEntity findForUpdate(Long id) {
        return classRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lớp hướng dẫn"));
    }
    private OrientationDtos.Person person(UserEntity user) { return new OrientationDtos.Person(user.getUserId(), user.getFullName(), user.getMssv(), user.getEmail()); }
    private String cell(Row row, int index) { Cell cell = row.getCell(index); return cell == null ? "" : new DataFormatter().formatCellValue(cell).trim(); }
}
