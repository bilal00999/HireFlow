package com.example.demo.assessment;

import com.example.demo.assessment.dto.AddQuestionsRequest;
import com.example.demo.assessment.dto.QuestionDto;
import com.example.demo.common.BadRequestException;
import com.example.demo.common.ResourceNotFoundException;
import com.example.demo.common.SecurityUtils;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * HR-side management of a job's assessment questions: appending a batch and
 * listing them. All operations verify the calling HR account owns the job, so
 * one company can never read or write another company's question bank.
 */
@Service
public class AssessmentQuestionService {

    private final AssessmentQuestionRepository questionRepository;
    private final JobRepository jobRepository;

    public AssessmentQuestionService(AssessmentQuestionRepository questionRepository,
                                     JobRepository jobRepository) {
        this.questionRepository = questionRepository;
        this.jobRepository = jobRepository;
    }

    /**
     * Appends questions to a job's assessment, continuing the existing order.
     * MCQ questions must carry options and a valid correct-option index.
     */
    @Transactional
    public List<QuestionDto> addQuestions(UUID jobId, AddQuestionsRequest request) {
        requireOwnedJob(jobId);

        // New questions are appended after any that already exist.
        int nextOrder = (int) questionRepository.countByJobId(jobId);

        for (AddQuestionsRequest.QuestionInput input : request.questions()) {
            AssessmentQuestion question = new AssessmentQuestion();
            question.setJobId(jobId);
            question.setQuestionText(input.questionText());
            question.setQuestionType(normalizeType(input.questionType()));
            question.setMaxScore(input.maxScore() != null ? input.maxScore() : 10);
            question.setOrderIndex(nextOrder++);

            if ("MCQ".equals(question.getQuestionType())) {
                applyMcqFields(question, input);
            }
            questionRepository.save(question);
        }
        return listQuestions(jobId);
    }

    /** Returns all questions for a job in order (HR view, includes answers). */
    @Transactional(readOnly = true)
    public List<QuestionDto> listQuestions(UUID jobId) {
        requireOwnedJob(jobId);
        return questionRepository.findByJobIdOrderByOrderIndexAsc(jobId)
                .stream().map(QuestionDto::from).toList();
    }

    private void applyMcqFields(AssessmentQuestion question, AddQuestionsRequest.QuestionInput input) {
        List<String> options = input.options();
        if (options == null || options.size() < 2) {
            throw new BadRequestException("MCQ questions must have at least two options");
        }
        Integer correct = input.correctOption();
        if (correct == null || correct < 0 || correct >= options.size()) {
            throw new BadRequestException(
                    "correctOption must be a valid 0-based index into options");
        }
        question.setOptions(options);
        question.setCorrectOption(correct);
    }

    private String normalizeType(String type) {
        String upper = type == null ? "" : type.trim().toUpperCase();
        if (!upper.equals("MCQ") && !upper.equals("TEXT")) {
            throw new BadRequestException("questionType must be MCQ or TEXT");
        }
        return upper;
    }

    // Ensures the job exists AND belongs to the calling HR account.
    private Job requireOwnedJob(UUID jobId) {
        UUID companyId = SecurityUtils.currentUserId();
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException("Job not found"));
        if (!job.getCompanyId().equals(companyId)) {
            throw new BadRequestException("You can only manage questions for your own jobs");
        }
        return job;
    }
}
