package com.archlens.backend.controller;

import com.archlens.backend.dto.AnalysisJobStatusResponseDto;
import com.archlens.backend.dto.CreateAnalysisJobRequest;
import com.archlens.backend.dto.CreateAnalysisJobResponseDto;
import com.archlens.backend.entity.User;
import com.archlens.backend.security.UserSessionService;
import com.archlens.backend.service.AnalysisJobService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/analysis", "/analysis"})
public class AnalysisJobController {

    private final AnalysisJobService analysisJobService;
    private final UserSessionService userSessionService;

    public AnalysisJobController(
            AnalysisJobService analysisJobService,
            UserSessionService userSessionService
    ) {
        this.analysisJobService = analysisJobService;
        this.userSessionService = userSessionService;
    }

    @PostMapping
    public ResponseEntity<CreateAnalysisJobResponseDto> createAnalysisJob(
            @RequestBody CreateAnalysisJobRequest request,
            HttpServletRequest servletRequest
    ) {
        Optional<User> currentUser = userSessionService.resolveUser(servletRequest);
        CreateAnalysisJobResponseDto response = analysisJobService.createJob(request, currentUser);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<AnalysisJobStatusResponseDto> getAnalysisJobStatus(
            @PathVariable("jobId") UUID jobId,
            HttpServletRequest servletRequest
    ) {
        Optional<User> currentUser = userSessionService.resolveUser(servletRequest);
        AnalysisJobStatusResponseDto response = analysisJobService.getJobStatus(jobId, currentUser);
        return ResponseEntity.ok(response);
    }
}
