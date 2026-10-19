package com.archlens.backend.security;

import com.archlens.backend.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

@Service
public class JwtService {

    public static final String SESSION_COOKIE_NAME = "archlens_session";
    private static final long DEFAULT_EXPIRATION_MS = Duration.ofDays(7).toMillis();

    private final SecretKey signingKey;

    public JwtService(@Value("${archlens.security.jwt-secret:archlens-default-jwt-secret-key-32-chars-long-or-more}") String secret) {
        this.signingKey = deriveKey(secret);
    }

    private SecretKey deriveKey(String secret) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha.digest(secret.getBytes(StandardCharsets.UTF_8));
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public String generateSessionToken(User user) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + DEFAULT_EXPIRATION_MS);

        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("username", user.getGithubUsername())
                .claim("avatarUrl", user.getAvatarUrl())
                .claim("githubId", user.getGithubId())
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    public Optional<UUID> extractUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(UUID.fromString(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public ResponseCookie createSessionCookie(String token, boolean secure) {
        return ResponseCookie.from(SESSION_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(Duration.ofDays(7))
                .sameSite("Lax")
                .build();
    }

    public ResponseCookie createLogoutCookie(boolean secure) {
        return ResponseCookie.from(SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .path("/")
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }
}
