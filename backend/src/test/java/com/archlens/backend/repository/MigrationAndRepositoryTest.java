package com.archlens.backend.repository;

import com.archlens.backend.entity.GithubCredential;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.entity.SystemGithubCredential;
import com.archlens.backend.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class MigrationAndRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SystemGithubCredentialRepository systemGithubCredentialRepository;

    @Autowired
    private GithubCredentialRepository githubCredentialRepository;

    @Autowired
    private RepoRepository repoRepository;

    @Test
    void testUserAndRepositoryPersistence() {
        User user = new User(123456L, "octocat", "octocat@github.com", "https://avatar.url");
        User savedUser = userRepository.save(user);

        assertNotNull(savedUser.getId());
        assertEquals("octocat", savedUser.getGithubUsername());

        Repository repo = new Repository(
                savedUser,
                987654L,
                "octocat/hello-world",
                "main",
                "user_oauth"
        );
        Repository savedRepo = repoRepository.save(repo);

        assertNotNull(savedRepo.getId());
        assertEquals("octocat/hello-world", savedRepo.getFullName());
        assertFalse(savedRepo.isAuthorizedForTesting());

        Optional<Repository> foundRepo = repoRepository.findByFullName("octocat/hello-world");
        assertTrue(foundRepo.isPresent());
        assertEquals(savedUser.getId(), foundRepo.get().getUser().getId());
    }

    @Test
    void testSystemGithubCredentialPersistence() {
        byte[] dummyEncrypted = "encrypted-secret-token".getBytes(StandardCharsets.UTF_8);
        String[] scopes = new String[]{"public_repo", "read:org"};
        SystemGithubCredential credential = new SystemGithubCredential(
                dummyEncrypted,
                scopes,
                Instant.now().plusSeconds(3600)
        );

        SystemGithubCredential saved = systemGithubCredentialRepository.save(credential);
        assertNotNull(saved.getId());

        Optional<SystemGithubCredential> active = systemGithubCredentialRepository.findActiveCredential();
        assertTrue(active.isPresent());
        assertEquals(saved.getId(), active.get().getId());
    }

    @Test
    void testGithubCredentialPersistence() {
        User user = userRepository.save(new User(222333L, "devuser", "dev@example.com", null));

        byte[] encryptedToken = "user-oauth-token".getBytes(StandardCharsets.UTF_8);
        GithubCredential credential = new GithubCredential(
                user,
                encryptedToken,
                new String[]{"repo", "read:user"},
                Instant.now().plusSeconds(86400)
        );

        GithubCredential saved = githubCredentialRepository.save(credential);
        assertNotNull(saved.getId());

        Optional<GithubCredential> found = githubCredentialRepository.findByUser(user);
        assertTrue(found.isPresent());
        assertEquals(user.getId(), found.get().getUser().getId());
    }
}
