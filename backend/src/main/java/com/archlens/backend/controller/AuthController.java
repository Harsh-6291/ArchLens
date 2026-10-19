package com.archlens.backend.controller;

import com.archlens.backend.dto.AuthCallbackResponseDto;
import com.archlens.backend.security.JwtService;
import com.archlens.backend.service.GitHubOAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping({"/api/v1/auth", "/auth"})
public class AuthController {

    private final GitHubOAuthService oauthService;
    private final JwtService jwtService;

    public AuthController(GitHubOAuthService oauthService, JwtService jwtService) {
        this.oauthService = oauthService;
        this.jwtService = jwtService;
    }

    @GetMapping("/github/login")
    public ResponseEntity<Void> loginWithGitHub(@RequestParam(value = "returnRepoUrl", required = false) String returnRepoUrl) {
        String authorizeUrl = oauthService.buildAuthorizeUrl(returnRepoUrl);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(authorizeUrl))
                .build();
    }

    @GetMapping("/github/callback")
    public ResponseEntity<AuthCallbackResponseDto> handleCallback(
            @RequestParam("code") String code,
            @RequestParam(value = "state", required = false) String state,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        GitHubOAuthService.CallbackProcessResult result = oauthService.handleOAuthCallback(code, state);

        ResponseCookie cookie = jwtService.createSessionCookie(result.sessionJwt(), request.isSecure());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(result.responseDto());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = jwtService.createLogoutCookie(request.isSecure());
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.noContent().build();
    }
}
