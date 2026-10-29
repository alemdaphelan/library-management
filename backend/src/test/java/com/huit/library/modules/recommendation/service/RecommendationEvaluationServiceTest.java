package com.huit.library.modules.recommendation.service;

import com.huit.library.modules.catalog.entity.BookEntity;
import com.huit.library.modules.circulation.repository.EvaluationInteractionProjection;
import com.huit.library.modules.circulation.repository.LoanDetailRepository;
import com.huit.library.modules.recommendation.config.RecommendationProperties;
import com.huit.library.modules.recommendation.dto.EvaluationMethodMetricsDTO;
import com.huit.library.modules.recommendation.dto.RecommendationEvaluationResponseDTO;
import com.huit.library.modules.recommendation.entity.BookSubjectAssignmentEntity;
import com.huit.library.modules.recommendation.entity.DepartmentSubjectAffinityEntity;
import com.huit.library.modules.recommendation.entity.RecommendationSubjectEntity;
import com.huit.library.modules.recommendation.repository.BookSubjectAssignmentRepository;
import com.huit.library.modules.recommendation.repository.DepartmentSubjectAffinityRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationEvaluationServiceTest {
    private final BookSubjectAssignmentRepository assignmentRepository =
            mock(BookSubjectAssignmentRepository.class);
    private final DepartmentSubjectAffinityRepository affinityRepository =
            mock(DepartmentSubjectAffinityRepository.class);
    private final LoanDetailRepository loanDetailRepository = mock(LoanDetailRepository.class);
    private final RecommendationEvaluationService service = new RecommendationEvaluationService(
            assignmentRepository,
            affinityRepository,
            loanDetailRepository,
            new TfIdfSimilarityEngine(),
            new RecommendationProperties());

    @Test
    void evaluatesThreeMethodsAndLabelsSparseRealHistorySeparately() {
        RecommendationSubjectEntity subjectA = subject(1L, "IT_DATA_AI", "CNTT - Dữ liệu - AI");
        RecommendationSubjectEntity subjectB = subject(2L, "FOOD_TECH", "Công nghệ thực phẩm");
        List<BookSubjectAssignmentEntity> assignments = new ArrayList<>();
        for (long id = 1; id <= 24; id++) {
            RecommendationSubjectEntity subject = id % 2 == 1 ? subjectA : subjectB;
            assignments.add(assignment(book(id, subject), subject));
        }

        when(assignmentRepository.findAllForEvaluation()).thenReturn(assignments);
        when(affinityRepository.findAllActiveForEvaluation()).thenReturn(List.of(
                affinity("cong nghe thong tin", subjectA),
                affinity("cong nghe thuc pham", subjectB)));
        EvaluationInteractionProjection firstInteraction = interaction(UUID.randomUUID(), 1L);
        EvaluationInteractionProjection secondInteraction = interaction(UUID.randomUUID(), 2L);
        when(loanDetailRepository.findEvaluationInteractions()).thenReturn(List.of(
                firstInteraction,
                secondInteraction));
        when(loanDetailRepository.findPopularityScores(any(), any(), anyDouble(), any()))
                .thenReturn(List.of());

        RecommendationEvaluationResponseDTO result = service.evaluate(5, 10);

        assertThat(result.evaluationDataset())
                .isEqualTo(RecommendationEvaluationService.PROXY_DATASET);
        assertThat(result.evaluatedScenarios()).isEqualTo(2);
        assertThat(result.methods()).extracting(EvaluationMethodMetricsDTO::method)
                .containsExactly("POPULARITY_BASELINE", "CONTENT_TFIDF", "PROPOSED_HYBRID");
        assertThat(result.methods()).allSatisfy(metric -> {
            assertThat(metric.precisionAtK()).isBetween(0.0, 1.0);
            assertThat(metric.recallAtK()).isBetween(0.0, 1.0);
            assertThat(metric.catalogCoverage()).isBetween(0.0, 1.0);
            assertThat(metric.totalRecommendations()).isEqualTo(10);
        });
        assertThat(metric(result, "PROPOSED_HYBRID").precisionAtK())
                .isGreaterThanOrEqualTo(metric(result, "POPULARITY_BASELINE").precisionAtK());
        assertThat(result.realHistory().interactions()).isEqualTo(2);
        assertThat(result.realHistory().eligibleUsers()).isZero();
        assertThat(result.realHistory().sufficientForPublication()).isFalse();
        assertThat(result.disclaimer()).contains("không phải hành vi mượn thật");
    }

    private EvaluationMethodMetricsDTO metric(
            RecommendationEvaluationResponseDTO result,
            String method) {
        return result.methods().stream()
                .filter(metric -> metric.method().equals(method))
                .findFirst()
                .orElseThrow();
    }

    private RecommendationSubjectEntity subject(Long id, String code, String name) {
        RecommendationSubjectEntity subject = new RecommendationSubjectEntity();
        subject.setSubjectId(id);
        subject.setCode(code);
        subject.setName(name);
        subject.setActive(true);
        return subject;
    }

    private BookEntity book(long id, RecommendationSubjectEntity subject) {
        BookEntity book = new BookEntity();
        book.setBookId(id);
        book.setTitle(subject.getCode() + " handbook " + id);
        book.setAuthor("Author " + id);
        book.setPublisher("HUIT Press");
        return book;
    }

    private BookSubjectAssignmentEntity assignment(
            BookEntity book,
            RecommendationSubjectEntity subject) {
        BookSubjectAssignmentEntity assignment = new BookSubjectAssignmentEntity();
        assignment.setBook(book);
        assignment.setSubject(subject);
        assignment.setConfidence(new BigDecimal("0.950"));
        assignment.setClassificationSource("KEYWORD_HIGH_CONFIDENCE");
        assignment.setPrimarySubject(true);
        return assignment;
    }

    private DepartmentSubjectAffinityEntity affinity(
            String department,
            RecommendationSubjectEntity subject) {
        DepartmentSubjectAffinityEntity affinity = new DepartmentSubjectAffinityEntity();
        affinity.setDepartmentKey(department);
        affinity.setDisplayName(department);
        affinity.setSubject(subject);
        affinity.setWeight(BigDecimal.ONE);
        affinity.setActive(true);
        return affinity;
    }

    private EvaluationInteractionProjection interaction(UUID userId, Long bookId) {
        EvaluationInteractionProjection projection = mock(EvaluationInteractionProjection.class);
        when(projection.getUserId()).thenReturn(userId);
        when(projection.getBookId()).thenReturn(bookId);
        when(projection.getInteractionAt()).thenReturn(LocalDateTime.now());
        return projection;
    }
}
