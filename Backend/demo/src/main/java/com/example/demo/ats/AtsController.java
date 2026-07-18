package com.example.demo.ats;

import com.example.demo.ats.dto.AtsResultDto;
import com.example.demo.common.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ats")
public class AtsController {

    private final AtsService atsService;

    public AtsController(AtsService atsService) {
        this.atsService = atsService;
    }

    // --- HR: view the ATS score breakdown for one applicant ---
    @GetMapping("/{applicationId}")
    @PreAuthorize("hasRole('HR')")
    public AtsResultDto getResult(@PathVariable UUID applicationId) {
        UUID companyId = SecurityUtils.currentUserId();
        AtsResult r = atsService.getForHr(applicationId, companyId);
        return new AtsResultDto(
                r.getApplicationId().toString(),
                r.getScore(),
                r.getSkillsMatch(),
                r.getExperienceMatch(),
                r.getEducationMatch(),
                r.getKeywordMatch(),
                r.getAiSummary(),
                r.getMatchedSkills(),
                r.getMissingSkills(),
                r.isPassed(),
                r.getReviewedAt());
    }
}
