package com.example.demo.assessment;

import com.example.demo.assessment.dto.AddQuestionsRequest;
import com.example.demo.assessment.dto.QuestionDto;
import com.example.demo.common.BadRequestException;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.common.SecurityUtils;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AssessmentQuestionService}: batch append with order
 * continuation, MCQ validation, the returned view, and the HR ownership guard.
 * The security context is stubbed so {@code SecurityUtils.currentUserId()}
 * returns the calling company's id.
 */
@ExtendWith(MockitoExtension.class)
class AssessmentQuestionServiceTest {

    @Mock AssessmentQuestionRepository questionRepository;
    @Mock JobRepository jobRepository;

    AssessmentQuestionService service;

    UUID companyId;
    UUID jobId;
    Job job;

    @BeforeEach
    void setUp() {
        service = new AssessmentQuestionService(questionRepository, jobRepository);

        companyId = UUID.randomUUID();
        jobId = UUID.randomUUID();

        job = new Job();
        job.setId(jobId);
        job.setCompanyId(companyId);

        // Authenticate as the owning company. SecurityUtils reads the principal name.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(companyId.toString(), null, List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addQuestions_appendsAfterExisting_andValidatesMcq() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(questionRepository.countByJobId(jobId)).thenReturn(2L); // two already exist
        when(questionRepository.save(any())).thenAnswer(inv -> {
            AssessmentQuestion q = inv.getArgument(0);
            if (q.getId() == null) q.setId(UUID.randomUUID());
            return q;
        });
        when(questionRepository.findByJobIdOrderByOrderIndexAsc(jobId)).thenReturn(List.of());

        AddQuestionsRequest req = new AddQuestionsRequest(List.of(
                new AddQuestionsRequest.QuestionInput(
                        "Which is O(1)?", "mcq", List.of("a", "b", "c"), 1, 10),
                new AddQuestionsRequest.QuestionInput(
                        "Explain GC.", "TEXT", null, null, 20)));

        service.addQuestions(jobId, req);

        // Two questions saved, order indices continuing from the existing count (2, 3).
        org.mockito.ArgumentCaptor<AssessmentQuestion> captor =
                org.mockito.ArgumentCaptor.forClass(AssessmentQuestion.class);
        verify(questionRepository, org.mockito.Mockito.times(2)).save(captor.capture());
        List<AssessmentQuestion> saved = captor.getAllValues();
        assertThat(saved.get(0).getQuestionType()).isEqualTo("MCQ");
        assertThat(saved.get(0).getOrderIndex()).isEqualTo(2);
        assertThat(saved.get(0).getCorrectOption()).isEqualTo(1);
        assertThat(saved.get(1).getQuestionType()).isEqualTo("TEXT");
        assertThat(saved.get(1).getOrderIndex()).isEqualTo(3);
        assertThat(saved.get(1).getMaxScore()).isEqualTo(20);
    }

    @Test
    void addQuestions_mcqWithBadCorrectIndex_rejected() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(questionRepository.countByJobId(jobId)).thenReturn(0L);

        AddQuestionsRequest req = new AddQuestionsRequest(List.of(
                new AddQuestionsRequest.QuestionInput(
                        "Pick one", "MCQ", List.of("a", "b"), 5, 10))); // index out of range

        assertThatThrownBy(() -> service.addQuestions(jobId, req))
                .isInstanceOf(BadRequestException.class);
        verify(questionRepository, never()).save(any());
    }

    @Test
    void addQuestions_mcqWithTooFewOptions_rejected() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        when(questionRepository.countByJobId(jobId)).thenReturn(0L);

        AddQuestionsRequest req = new AddQuestionsRequest(List.of(
                new AddQuestionsRequest.QuestionInput(
                        "Pick one", "MCQ", List.of("only-one"), 0, 10)));

        assertThatThrownBy(() -> service.addQuestions(jobId, req))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void addQuestions_unknownType_rejected() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));

        AddQuestionsRequest req = new AddQuestionsRequest(List.of(
                new AddQuestionsRequest.QuestionInput("q", "ESSAY", null, null, 10)));

        assertThatThrownBy(() -> service.addQuestions(jobId, req))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void addQuestions_foreignJob_rejected() {
        Job otherCompanyJob = new Job();
        otherCompanyJob.setId(jobId);
        otherCompanyJob.setCompanyId(UUID.randomUUID()); // different owner
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(otherCompanyJob));

        AddQuestionsRequest req = new AddQuestionsRequest(List.of(
                new AddQuestionsRequest.QuestionInput("q", "TEXT", null, null, 10)));

        assertThatThrownBy(() -> service.addQuestions(jobId, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("your own jobs");
    }

    @Test
    void listQuestions_missingJob_throwsNotFound() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listQuestions(jobId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listQuestions_returnsMappedDtos() {
        when(jobRepository.findById(jobId)).thenReturn(Optional.of(job));
        AssessmentQuestion q = new AssessmentQuestion();
        q.setId(UUID.randomUUID());
        q.setJobId(jobId);
        q.setQuestionType("MCQ");
        q.setQuestionText("Which is O(1)?");
        q.setOptions(List.of("a", "b"));
        q.setCorrectOption(0);
        q.setMaxScore(10);
        q.setOrderIndex(0);
        when(questionRepository.findByJobIdOrderByOrderIndexAsc(jobId)).thenReturn(List.of(q));

        List<QuestionDto> dtos = service.listQuestions(jobId);

        assertThat(dtos).hasSize(1);
        assertThat(dtos.get(0).correctOption()).isEqualTo(0); // HR view includes the key
        assertThat(dtos.get(0).options()).containsExactly("a", "b");
    }
}
