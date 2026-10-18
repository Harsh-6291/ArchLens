package com.archlens.backend.controller;

import com.archlens.backend.dto.CreateRepoRequest;
import com.archlens.backend.dto.RepoResponseDto;
import com.archlens.backend.service.RepoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/repos", "/repos"})
public class RepoController {

    private final RepoService repoService;

    public RepoController(RepoService repoService) {
        this.repoService = repoService;
    }

    @PostMapping
    public ResponseEntity<RepoResponseDto> createRepo(@RequestBody CreateRepoRequest request) {
        RepoResponseDto response = repoService.importPublicRepo(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
