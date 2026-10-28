package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.dto.ShelfSubjectDTO;
import com.huit.library.modules.catalog.dto.ShelfSubjectRuleRequestDTO;
import com.huit.library.modules.catalog.entity.ShelfEntity;
import com.huit.library.modules.catalog.entity.ShelfSubjectRuleEntity;
import com.huit.library.modules.catalog.repository.ShelfRepository;
import com.huit.library.modules.catalog.repository.ShelfSubjectRuleRepository;
import com.huit.library.modules.recommendation.entity.RecommendationSubjectEntity;
import com.huit.library.modules.recommendation.repository.RecommendationSubjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class ShelfSubjectRuleService {
    private final ShelfRepository shelfRepository;
    private final ShelfSubjectRuleRepository ruleRepository;
    private final RecommendationSubjectRepository subjectRepository;

    public ShelfSubjectRuleService(
            ShelfRepository shelfRepository,
            ShelfSubjectRuleRepository ruleRepository,
            RecommendationSubjectRepository subjectRepository) {
        this.shelfRepository = shelfRepository;
        this.ruleRepository = ruleRepository;
        this.subjectRepository = subjectRepository;
    }

    @Transactional(readOnly = true)
    public List<ShelfSubjectDTO> getAvailableSubjects() {
        return subjectRepository.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ShelfSubjectDTO> getShelfSubjects(Long shelfId) {
        requireShelf(shelfId);
        return ruleRepository.findByShelf_ShelfIdAndActiveTrueOrderBySubject_NameAsc(shelfId)
                .stream()
                .map(rule -> toDto(rule.getSubject()))
                .toList();
    }

    @Transactional
    public List<ShelfSubjectDTO> replaceShelfSubjects(Long shelfId, ShelfSubjectRuleRequestDTO request) {
        ShelfEntity shelf = requireShelf(shelfId);
        Set<Long> requestedIds = new LinkedHashSet<>(request.subjectIds());
        List<RecommendationSubjectEntity> subjects = requestedIds.isEmpty()
                ? List.of()
                : subjectRepository.findBySubjectIdInAndActiveTrue(requestedIds);

        if (subjects.size() != requestedIds.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Có chủ đề không tồn tại hoặc đã ngừng sử dụng"
            );
        }

        ruleRepository.deleteByShelf_ShelfId(shelfId);
        // Force deletion before inserting replacement rows. Hibernate may
        // otherwise insert first and violate the (shelf_id, subject_id) key
        // when an existing subject remains selected.
        ruleRepository.flush();
        if (!subjects.isEmpty()) {
            List<ShelfSubjectRuleEntity> rules = subjects.stream().map(subject -> {
                ShelfSubjectRuleEntity rule = new ShelfSubjectRuleEntity();
                rule.setShelf(shelf);
                rule.setSubject(subject);
                rule.setActive(true);
                return rule;
            }).toList();
            ruleRepository.saveAll(rules);
        }

        return subjects.stream()
                .sorted(java.util.Comparator.comparing(RecommendationSubjectEntity::getName))
                .map(this::toDto)
                .toList();
    }

    private ShelfEntity requireShelf(Long shelfId) {
        return shelfRepository.findById(shelfId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy ngăn kệ"));
    }

    private ShelfSubjectDTO toDto(RecommendationSubjectEntity subject) {
        return new ShelfSubjectDTO(
                subject.getSubjectId(), subject.getCode(), subject.getName(), subject.getDescription());
    }
}
