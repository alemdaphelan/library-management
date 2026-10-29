package com.huit.library.modules.penalty.service;

import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.penalty.dto.LibraryViolationRequestDTO;
import com.huit.library.modules.penalty.entity.LibraryViolationEntity;
import com.huit.library.modules.penalty.repository.LibraryViolationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class LibraryViolationService {
    private final LibraryViolationRepository violationRepository;
    private final UserRepository userRepository;

    public LibraryViolationService(
            LibraryViolationRepository violationRepository, UserRepository userRepository) {
        this.violationRepository = violationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public LibraryViolationEntity record(LibraryViolationRequestDTO request, UUID operatorId) {
        if (!userRepository.existsById(request.getUserId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy bạn đọc");
        }
        String type = request.getViolationType().trim().toUpperCase(Locale.ROOT);
        if (!"UNAUTHORIZED_REMOVAL".equals(type)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Loại vi phạm hỗ trợ hiện tại là UNAUTHORIZED_REMOVAL");
        }
        LibraryViolationEntity violation = new LibraryViolationEntity();
        violation.setUserId(request.getUserId());
        violation.setTicketId(request.getTicketId());
        violation.setBarcode(request.getBarcode());
        violation.setViolationType(type);
        violation.setDescription(request.getDescription().trim());
        violation.setActionTaken(request.getActionTaken());
        violation.setRecordedBy(operatorId);
        violation.setOccurredAt(request.getOccurredAt() == null
                ? LocalDateTime.now() : request.getOccurredAt());
        return violationRepository.save(violation);
    }

    @Transactional(readOnly = true)
    public List<LibraryViolationEntity> getByUser(UUID userId) {
        return violationRepository.findByUserIdOrderByOccurredAtDesc(userId);
    }
}
