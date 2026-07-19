package com.example.demo.application;

import com.example.demo.auth.Company;
import com.example.demo.auth.CompanyRepository;
import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.common.JwtUtil;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcConfigurer;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

import org.springframework.web.context.WebApplicationContext;

/**
 * End-to-end coverage of the candidate application flow. Drives the real HTTP
 * stack (security filter chain, @PreAuthorize, multipart parsing, JPA) against
 * an in-memory H2 database. Auth is exercised with genuine JWTs minted by
 * {@link JwtUtil}, so role enforcement is tested for real rather than mocked.
 */
@SpringBootTest
class ApplicationFlowIntegrationTest {

    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository userRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private ApplicationRepository applicationRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;

    private MockMvc mockMvc;

    private UUID candidateId;
    private String candidateToken;
    private UUID companyId;
    private String hrToken;
    private UUID activeJobId;

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply((MockMvcConfigurer) springSecurity()).build();

        applicationRepository.deleteAll();
        jobRepository.deleteAll();
        userRepository.deleteAll();
        companyRepository.deleteAll();

        User candidate = new User();
        candidate.setEmail("cand@example.com");
        candidate.setPasswordHash(passwordEncoder.encode("secret123"));
        candidate.setFullName("Casey Candidate");
        candidate = userRepository.save(candidate);
        candidateId = candidate.getId();
        candidateToken = jwtUtil.generateToken(candidateId.toString(), candidate.getEmail(), "CANDIDATE");

        Company company = new Company();
        company.setName("Acme Corp");
        company.setEmail("hr@example.com");
        company.setPasswordHash(passwordEncoder.encode("secret123"));
        company = companyRepository.save(company);
        companyId = company.getId();
        hrToken = jwtUtil.generateToken(companyId.toString(), company.getEmail(), "HR");

        Job job = new Job();
        job.setCompanyId(companyId);
        job.setTitle("Backend Developer");
        job.setDescription("Build APIs.");
        job.setStatus("ACTIVE");
        activeJobId = jobRepository.save(job).getId();
    }

    private MockMultipartFile resumeFile() {
        return new MockMultipartFile(
                "resume", "resume.pdf", "application/pdf", "dummy pdf bytes".getBytes());
    }

    @Test
    void candidateAppliesSuccessfully_thenSeesItInMyApplications() throws Exception {
        mockMvc.perform(multipart("/api/v1/applications")
                        .file(resumeFile())
                        .param("jobId", activeJobId.toString())
                        .param("coverLetter", "I would love this role.")
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stage").value("APPLIED"))
                .andExpect(jsonPath("$.applicationId").isNotEmpty());

        mockMvc.perform(get("/api/v1/applications/my")
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].job.title").value("Backend Developer"))
                // Stage is not asserted here: ATS scoring runs asynchronously
                // right after apply, so by the time this GET executes the stage
                // may have already advanced past APPLIED. We only verify the
                // application surfaces in "my applications".
                .andExpect(jsonPath("$[0].stage").isNotEmpty());
    }

    @Test
    void duplicateApplicationIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/applications")
                        .file(resumeFile())
                        .param("jobId", activeJobId.toString())
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated());

        mockMvc.perform(multipart("/api/v1/applications")
                        .file(resumeFile())
                        .param("jobId", activeJobId.toString())
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nonPdfResumeIsRejected() throws Exception {
        MockMultipartFile txt = new MockMultipartFile(
                "resume", "resume.txt", "text/plain", "not a resume".getBytes());

        mockMvc.perform(multipart("/api/v1/applications")
                        .file(txt)
                        .param("jobId", activeJobId.toString())
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void applyingToClosedJobIsRejected() throws Exception {
        Job closed = new Job();
        closed.setCompanyId(companyId);
        closed.setTitle("Old Role");
        closed.setDescription("Filled.");
        closed.setStatus("CLOSED");
        UUID closedJobId = jobRepository.save(closed).getId();

        mockMvc.perform(multipart("/api/v1/applications")
                        .file(resumeFile())
                        .param("jobId", closedJobId.toString())
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hrCannotApply() throws Exception {
        mockMvc.perform(multipart("/api/v1/applications")
                        .file(resumeFile())
                        .param("jobId", activeJobId.toString())
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedApplyIsRejected() throws Exception {
        mockMvc.perform(multipart("/api/v1/applications")
                        .file(resumeFile())
                        .param("jobId", activeJobId.toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void hrSeesApplicantsForOwnJob() throws Exception {
        mockMvc.perform(multipart("/api/v1/applications")
                        .file(resumeFile())
                        .param("jobId", activeJobId.toString())
                        .header("Authorization", "Bearer " + candidateToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/applications/job/" + activeJobId)
                        .header("Authorization", "Bearer " + hrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].candidateName").value("Casey Candidate"))
                .andExpect(jsonPath("$[0].candidateEmail").value("cand@example.com"));
    }
}
