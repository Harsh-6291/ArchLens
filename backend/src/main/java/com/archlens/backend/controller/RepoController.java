package com.archlens.backend.controller;

import com.archlens.backend.dto.CreateRepoRequest;
import com.archlens.backend.dto.RepoResponseDto;
import com.archlens.backend.entity.User;
import com.archlens.backend.security.UserSessionService;
import com.archlens.backend.service.RepoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping({"/api/v1/repos", "/repos"})
public class RepoController {

    private final RepoService repoService;
    private final UserSessionService userSessionService;

    public RepoController(RepoService repoService, UserSessionService userSessionService) {
        this.repoService = repoService;
        this.userSessionService = userSessionService;
    }

    @PostMapping
    public ResponseEntity<RepoResponseDto> createRepo(
            @RequestBody CreateRepoRequest request,
            HttpServletRequest servletRequest
    ) {
        Optional<User> currentUser = userSessionService.resolveUser(servletRequest);
        RepoResponseDto response = repoService.importRepo(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
