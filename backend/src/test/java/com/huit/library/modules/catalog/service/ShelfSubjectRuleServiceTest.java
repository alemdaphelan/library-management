package com.huit.library.modules.catalog.service;

import com.huit.library.modules.catalog.dto.ShelfSubjectRuleRequestDTO;
import com.huit.library.modules.catalog.entity.ShelfEntity;
import com.huit.library.modules.catalog.repository.ShelfRepository;
import com.huit.library.modules.catalog.repository.ShelfSubjectRuleRepository;
import com.huit.library.modules.recommendation.entity.RecommendationSubjectEntity;
import com.huit.library.modules.recommendation.repository.RecommendationSubjectRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class ShelfSubjectRuleServiceTest {
    @Test
    void replaceShelfSubjectsPersistsTheSelectedActiveSubjects() {
        ShelfRepository shelfRepository = mock(ShelfRepository.class);
        ShelfSubjectRuleRepository ruleRepository = mock(ShelfSubjectRuleRepository.class);
        RecommendationSubjectRepository subjectRepository = mock(RecommendationSubjectRepository.class);
        ShelfSubjectRuleService service = new ShelfSubjectRuleService(
                shelfRepository, ruleRepository, subjectRepository);
        ShelfEntity shelf = new ShelfEntity();
        shelf.setShelfId(5L);
        RecommendationSubjectEntity it = subject(8L, "IT_DATA_AI", "CNTT - Dữ liệu - AI");
        RecommendationSubjectEntity network = subject(9L, "NETWORK", "Mạng máy tính");
        when(shelfRepository.findById(5L)).thenReturn(Optional.of(shelf));
        when(subjectRepository.findBySubjectIdInAndActiveTrue(Set.of(8L, 9L)))
                .thenReturn(List.of(network, it));

        var result = service.replaceShelfSubjects(
                5L, new ShelfSubjectRuleRequestDTO(Set.of(8L, 9L)));

        assertThat(result).extracting(item -> item.name())
                .containsExactly("CNTT - Dữ liệu - AI", "Mạng máy tính");
        verify(ruleRepository).deleteByShelf_ShelfId(5L);
        verify(ruleRepository).flush();
        verify(ruleRepository).saveAll(anyList());
    }

    @Test
    void replaceShelfSubjectsRejectsUnknownOrInactiveSubjects() {
        ShelfRepository shelfRepository = mock(ShelfRepository.class);
        RecommendationSubjectRepository subjectRepository = mock(RecommendationSubjectRepository.class);
        ShelfSubjectRuleService service = new ShelfSubjectRuleService(
                shelfRepository, mock(ShelfSubjectRuleRepository.class), subjectRepository);
        ShelfEntity shelf = new ShelfEntity();
        shelf.setShelfId(5L);
        when(shelfRepository.findById(5L)).thenReturn(Optional.of(shelf));
        when(subjectRepository.findBySubjectIdInAndActiveTrue(Set.of(99L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.replaceShelfSubjects(
                5L, new ShelfSubjectRuleRequestDTO(Set.of(99L))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("không tồn tại");
    }

    private RecommendationSubjectEntity subject(Long id, String code, String name) {
        RecommendationSubjectEntity subject = new RecommendationSubjectEntity();
        subject.setSubjectId(id);
        subject.setCode(code);
        subject.setName(name);
        subject.setActive(true);
        return subject;
    }
}
