package com.huit.library.modules.interactive.service;

import com.huit.library.modules.auth.entity.UserEntity;
import com.huit.library.modules.auth.repository.UserRepository;
import com.huit.library.modules.interactive.dto.CreateProposalRequestDTO;
import com.huit.library.modules.interactive.dto.ProposalResponseDTO;
import com.huit.library.modules.interactive.dto.UpdateProposalStatusDTO;
import com.huit.library.modules.interactive.entity.BookProposalEntity;
import com.huit.library.modules.interactive.repository.BookProposalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class BookProposalService {

    private final BookProposalRepository proposalRepository;
    private final UserRepository userRepository;

    public BookProposalService(BookProposalRepository proposalRepository, UserRepository userRepository) {
        this.proposalRepository = proposalRepository;
        this.userRepository = userRepository;
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

        if (req.getStatus() != null && !req.getStatus().trim().isEmpty()) {
            String statusUpper = req.getStatus().trim().toUpperCase();
            if ("APPROVED".equals(statusUpper) || "REJECTED".equals(statusUpper) || "PENDING".equals(statusUpper)) {
                entity.setStatus(statusUpper);
            }
        }

        if (req.getAdminNote() != null) {
            entity.setAdminNote(req.getAdminNote().trim());
        }

        BookProposalEntity updated = proposalRepository.save(entity);
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
        dto.setReason(entity.getReason());
        dto.setNote(entity.getNote());
        dto.setAdminNote(entity.getAdminNote());
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
}
