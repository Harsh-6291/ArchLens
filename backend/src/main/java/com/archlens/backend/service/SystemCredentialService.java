package com.archlens.backend.service;

import com.archlens.backend.entity.SystemGithubCredential;
import com.archlens.backend.repository.SystemGithubCredentialRepository;
import com.archlens.backend.security.EncryptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class SystemCredentialService implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemCredentialService.class);

    private final SystemGithubCredentialRepository repository;
    private final EncryptionService encryptionService;
    private final String envSystemToken;

    public SystemCredentialService(
            SystemGithubCredentialRepository repository,
            EncryptionService encryptionService,
            @Value("${archlens.github.system-token:}") String envSystemToken
    ) {
        this.repository = repository;
        this.encryptionService = encryptionService;
        this.envSystemToken = envSystemToken;
    }

    @Transactional
    public SystemGithubCredential storeSystemToken(String rawToken, String[] scopes, Instant expiresAt) {
        byte[] encrypted = encryptionService.encrypt(rawToken);
        SystemGithubCredential credential = new SystemGithubCredential(encrypted, scopes, expiresAt);
        log.info("Persisting encrypted system GitHub credential with scopes: {}", (Object) scopes);
        return repository.save(credential);
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (envSystemToken != null && !envSystemToken.isBlank()) {
            if (repository.findActiveCredential().isEmpty()) {
                log.info("Seeding system GitHub credential from environment configuration");
                storeSystemToken(envSystemToken, new String[]{"public_repo"}, null);
            }
        }
    }
}
