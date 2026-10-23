package com.huit.library.modules.interactive.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.catalog.entity.CategoryEntity;
import com.huit.library.modules.catalog.repository.BookRepository;
import com.huit.library.modules.catalog.repository.CategoryRepository;
import com.huit.library.modules.interactive.dto.CreateProposalRequestDTO;
import com.huit.library.modules.interactive.dto.ProposalResponseDTO;
import com.huit.library.modules.interactive.dto.UpdateProposalStatusDTO;
import com.huit.library.modules.interactive.entity.BookProposalEntity;
import com.huit.library.modules.interactive.repository.BookProposalRepository;
import com.huit.library.modules.notification.service.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookProposalService {

    private final BookProposalRepository proposalRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;

    public BookProposalService(BookProposalRepository proposalRepository,
                               UserRepository userRepository,
                               NotificationService notificationService,
                               BookRepository bookRepository,
                               CategoryRepository categoryRepository) {
        this.proposalRepository = proposalRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.bookRepository = bookRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public ProposalResponseDTO createProposal(UUID userId, CreateProposalRequestDTO req) {
        if (req.getTitle() == null || req.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException("Tên sách đề xuất không được để trống!");
        }

        BookProposalEntity entity = new BookProposalEntity();
        entity.setUserId(userId);
        entity.setTitle(req.getTitle().trim());
        entity.setAuthorName(req.getAuthorName() != null ? req.getAuthorName().trim() : null);
        entity.setPublisher(req.getPublisher() != null ? req.getPublisher().trim() : null);
        entity.setIsbn(normalize(req.getIsbn()));
        entity.setPublicationYear(req.getPublicationYear());
        entity.setEdition(normalize(req.getEdition()));
        entity.setEstimatedPrice(req.getEstimatedPrice());
        entity.setExternalUrl(normalize(req.getExternalUrl()));
        entity.setGenre(normalize(req.getGenre()));
        entity.setUrgencyLevel(req.getUrgencyLevel() == null || req.getUrgencyLevel().isBlank()
                ? "NORMAL" : req.getUrgencyLevel().trim().toUpperCase());
        entity.setReason(req.getReason() != null ? req.getReason().trim() : null);
        entity.setNote(req.getNote() != null ? req.getNote().trim() : null);
        entity.setStatus("PENDING");

        BookProposalEntity saved = proposalRepository.save(entity);

        UserEntity user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        return mapToDTO(saved, user);
    }

    @Transactional(readOnly = true)
    public List<ProposalResponseDTO> getMyProposals(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        UserEntity user = userRepository.findById(userId).orElse(null);
        List<BookProposalEntity> list = proposalRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return list.stream()
                .map(entity -> mapToDTO(entity, user))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ProposalResponseDTO> getAllProposals() {
        List<BookProposalEntity> list = proposalRepository.findAllByOrderByCreatedAtDesc();
        
        Set<UUID> userIds = list.stream()
                .map(BookProposalEntity::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, UserEntity> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getUserId, u -> u));

        return list.stream()
                .map(entity -> mapToDTO(entity, entity.getUserId() != null ? userMap.get(entity.getUserId()) : null))
                .collect(Collectors.toList());
    }

    @Transactional
    public ProposalResponseDTO updateStatus(Long proposalId, UpdateProposalStatusDTO req) {
        BookProposalEntity entity = proposalRepository.findById(proposalId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy đề xuất mã #" + proposalId));

        if (req.getStatus() == null || req.getStatus().isBlank()) {
            throw new IllegalArgumentException("Trạng thái xử lý không được để trống");
        }
        String statusUpper = req.getStatus().trim().toUpperCase();
        if (!"APPROVED".equals(statusUpper) && !"REJECTED".equals(statusUpper)) {
            throw new IllegalArgumentException("Trạng thái đề xuất chỉ có thể là APPROVED hoặc REJECTED");
        }
        String currentStatus = entity.getStatus() == null ? "PENDING" : entity.getStatus().toUpperCase();
        if (!"PENDING".equals(currentStatus)) {
            if (currentStatus.equals(statusUpper)) {
                UserEntity existingUser = entity.getUserId() == null
                        ? null : userRepository.findById(entity.getUserId()).orElse(null);
                return mapToDTO(entity, existingUser);
            }
            throw new IllegalStateException("Đề xuất đã được xử lý và không thể đổi kết quả");
        }

        if ("REJECTED".equals(statusUpper)
                && (req.getAdminNote() == null || req.getAdminNote().isBlank())) {
            throw new IllegalArgumentException("Phải nhập lý do khi từ chối đề xuất");
        }

        entity.setStatus(statusUpper);
        if (req.getAdminNote() != null) {
            entity.setAdminNote(req.getAdminNote().trim());
        }

        if ("APPROVED".equals(statusUpper)) {
            entity.setApprovedBookId(createCatalogEntry(entity).getBookId());
        }

        BookProposalEntity updated = proposalRepository.save(entity);
        if (entity.getUserId() != null && ("APPROVED".equals(entity.getStatus())
                || "REJECTED".equals(entity.getStatus()))) {
            String type = "APPROVED".equals(entity.getStatus())
                    ? "PROPOSAL_APPROVED" : "PROPOSAL_REJECTED";
            String result = "APPROVED".equals(entity.getStatus())
                    ? "đã được chấp nhận" : "đã bị từ chối";
            String note = entity.getAdminNote() == null || entity.getAdminNote().isBlank()
                    ? "" : " Lý do/ghi chú: " + entity.getAdminNote().trim();
            notificationService.createOnce(
                    entity.getUserId(), type,
                    "Đề xuất tài liệu ‘" + entity.getTitle() + "’ " + result + "." + note,
                    "BOOK_PROPOSAL", String.valueOf(entity.getProposalId()),
                    "proposal:" + entity.getProposalId() + ":" + entity.getStatus(),
                    java.time.LocalDateTime.now());
        }
        UserEntity user = entity.getUserId() != null ? userRepository.findById(entity.getUserId()).orElse(null) : null;
        return mapToDTO(updated, user);
    }

    private ProposalResponseDTO mapToDTO(BookProposalEntity entity, UserEntity user) {
        ProposalResponseDTO dto = new ProposalResponseDTO();
        dto.setProposalId(entity.getProposalId());
        dto.setUserId(entity.getUserId());
        dto.setTitle(entity.getTitle());
        dto.setAuthorName(entity.getAuthorName());
        dto.setPublisher(entity.getPublisher());
        dto.setIsbn(entity.getIsbn());
        dto.setPublicationYear(entity.getPublicationYear());
        dto.setEdition(entity.getEdition());
        dto.setEstimatedPrice(entity.getEstimatedPrice());
        dto.setExternalUrl(entity.getExternalUrl());
        dto.setGenre(entity.getGenre());
        dto.setUrgencyLevel(entity.getUrgencyLevel());
        dto.setReason(entity.getReason());
        dto.setNote(entity.getNote());
        dto.setAdminNote(entity.getAdminNote());
        dto.setApprovedBookId(entity.getApprovedBookId());
        dto.setStatus(entity.getStatus() != null ? entity.getStatus() : "PENDING");
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        if (user != null) {
            dto.setUserName(user.getFullName());
            dto.setUserMssv(user.getMssv());
            dto.setUserEmail(user.getEmail());
        }
        return dto;
    }

    private BookEntity createCatalogEntry(BookProposalEntity proposal) {
        if (proposal.getApprovedBookId() != null) {
            return bookRepository.findById(proposal.getApprovedBookId())
                    .orElseThrow(() -> new IllegalStateException("Đầu sách đã duyệt không còn tồn tại"));
        }
        if (proposal.getIsbn() != null) {
            Optional<BookEntity> existing = bookRepository.findFirstByIsbn(proposal.getIsbn());
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        BookEntity book = new BookEntity();
        book.setTitle(proposal.getTitle());
        book.setAuthor(proposal.getAuthorName());
        book.setPublisher(proposal.getPublisher());
        book.setIsbn(proposal.getIsbn());
        book.setPublishYear(proposal.getPublicationYear());
        book.setDefaultPrice(proposal.getEstimatedPrice());
        book.setIsDigital(false);
        book.setViewCount(0L);
        book.setCategory(resolveCategory(proposal.getGenre()));
        book.setDescription(catalogDescription(proposal));
        return bookRepository.save(book);
    }

    private CategoryEntity resolveCategory(String genre) {
        if (genre == null || genre.isBlank()) {
            return null;
        }
        return categoryRepository.findFirstByNameIgnoreCase(genre.trim())
                .orElseGet(() -> {
                    CategoryEntity category = new CategoryEntity();
                    category.setName(genre.trim());
                    category.setDescription("Thể loại được bổ sung từ đề xuất tài liệu đã duyệt");
                    return categoryRepository.save(category);
                });
    }

    private String catalogDescription(BookProposalEntity proposal) {
        List<String> details = new ArrayList<>();
        details.add("Đầu sách được tạo từ đề xuất #" + proposal.getProposalId()
                + "; chưa có bản sao và cần nhập kho bằng phiếu nhập.");
        if (proposal.getEdition() != null) {
            details.add("Phiên bản: " + proposal.getEdition() + ".");
        }
        if (proposal.getExternalUrl() != null) {
            details.add("Nguồn tham khảo: " + proposal.getExternalUrl());
        }
        return String.join(" ", details);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
