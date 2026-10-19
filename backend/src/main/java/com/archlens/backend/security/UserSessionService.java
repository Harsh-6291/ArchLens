package com.archlens.backend.security;

import com.archlens.backend.entity.User;
import com.archlens.backend.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserSessionService {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public UserSessionService(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    public Optional<User> resolveUser(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        // 1. Try httpOnly cookie first
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (JwtService.SESSION_COOKIE_NAME.equals(cookie.getName())) {
                    Optional<User> user = extractUserFromToken(cookie.getValue());
                    if (user.isPresent()) {
                        return user;
                    }
                }
            }
        }

        // 2. Fallback to Authorization: Bearer <token>
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            return extractUserFromToken(token);
        }

        return Optional.empty();
    }

    private Optional<User> extractUserFromToken(String token) {
        return jwtService.extractUserId(token)
                .flatMap(userRepository::findById);
    }
}
