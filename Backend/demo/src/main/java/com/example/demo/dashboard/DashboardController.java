package com.example.demo.dashboard;

import com.example.demo.dashboard.dto.DashboardStatsDto;
import com.example.demo.dashboard.dto.PipelineDto;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * HR-only dashboard endpoints. Both views are scoped to the caller's company
 * inside {@link DashboardService}. Mirrors 04-API-DESIGN.md.
 */
@RestController
@RequestMapping("/api/v1/hr")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // --- Overview stats across the company's jobs ---
    @GetMapping("/dashboard")
    @PreAuthorize("hasRole('HR')")
    public DashboardStatsDto dashboard() {
        return dashboardService.overview();
    }

    // --- Full pipeline (stage counts + applicants) for one job ---
    @GetMapping("/pipeline/{jobId}")
    @PreAuthorize("hasRole('HR')")
    public PipelineDto pipeline(@PathVariable UUID jobId) {
        return dashboardService.pipeline(jobId);
    }
}
