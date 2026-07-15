package com.example.demo.config;

import com.example.demo.auth.Company;
import com.example.demo.auth.CompanyRepository;
import com.example.demo.auth.User;
import com.example.demo.auth.UserRepository;
import com.example.demo.job.Job;
import com.example.demo.job.JobRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.seed-demo-data", havingValue = "true", matchIfMissing = true)
public class DemoDataInitializer implements CommandLineRunner {

    private static final String HR_EMAIL = "demo.hr@hireflow.local";
    private static final String CANDIDATE_EMAIL = "demo.candidate@hireflow.local";
    private static final String DEMO_PASSWORD = "Password123!";

    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(CompanyRepository companyRepository,
                               UserRepository userRepository,
                               JobRepository jobRepository,
                               PasswordEncoder passwordEncoder) {
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.jobRepository = jobRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Company demoCompany = companyRepository.findByEmail(HR_EMAIL)
                .orElseGet(this::createDemoCompany);
        ensureDemoCandidate();
        ensureDemoJob(demoCompany);
    }

    private Company createDemoCompany() {
        Company company = new Company();
        company.setName("HireFlow Demo HR");
        company.setEmail(HR_EMAIL);
        company.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        company.setIndustry("Technology");
        company.setWebsite("https://hireflow.local");
        return companyRepository.save(company);
    }

    private void ensureDemoCandidate() {
        if (userRepository.findByEmail(CANDIDATE_EMAIL).isPresent()) {
            return;
        }

        User user = new User();
        user.setFullName("Demo Candidate");
        user.setEmail(CANDIDATE_EMAIL);
        user.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        user.setPhone("+1-555-0100");
        userRepository.save(user);
    }

    private void ensureDemoJob(Company company) {
        if (!jobRepository.search(null, null, null, PageRequest.of(0, 1)).isEmpty()) {
            return;
        }

        Job job = new Job();
        job.setCompanyId(company.getId());
        job.setTitle("Backend Java Developer");
        job.setDescription("Build and extend the HireFlow backend API for candidate and HR workflows.");
        job.setRequirements("Java, Spring Boot, PostgreSQL, REST APIs");
        job.setRequiredSkills(List.of("Java", "Spring Boot", "PostgreSQL", "REST APIs"));
        job.setJobType("FULL_TIME");
        job.setLocation("Remote");
        job.setSalaryMin(90000);
        job.setSalaryMax(130000);
        job.setCurrency("USD");
        job.setAtsMinScore(60);
        job.setAssessmentPassScore(70);
        job.setAssessmentTimeLimit(30);
        job.setInterviewTopics(List.of("Java", "Spring Boot", "System Design"));
        job.setInterviewDuration(25);
        job.setInterviewNumQuestions(5);
        job.setStatus("ACTIVE");
        job.setDeadline(LocalDate.now().plusDays(30));
        jobRepository.save(job);
    }
}