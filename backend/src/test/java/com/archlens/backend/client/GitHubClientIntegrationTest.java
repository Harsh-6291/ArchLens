package com.archlens.backend.client;

import com.archlens.backend.dto.GitHubRepoDto;
import com.archlens.backend.entity.SystemGithubCredential;
import com.archlens.backend.repository.SystemGithubCredentialRepository;
import com.archlens.backend.service.SystemCredentialService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class GitHubClientIntegrationTest {

    @Autowired
    private GitHubClient gitHubClient;

    @Autowired
    private SystemCredentialService systemCredentialService;

    @Autowired
    private SystemGithubCredentialRepository systemGithubCredentialRepository;

    @Test
    void testSystemTokenEncryptionAndDecryptionFlow() {
        String testToken = "ghp_mock_system_token_1234567890";
        SystemGithubCredential saved = systemCredentialService.storeSystemToken(
                testToken,
                new String[]{"public_repo"},
                null
        );

        assertNotNull(saved.getId());

        Optional<String> decrypted = gitHubClient.getDecryptedSystemToken();
        assertTrue(decrypted.isPresent());
        assertEquals(testToken, decrypted.get());
    }

    @Test
    void testFetchPublicRepoMetadataFromGitHubApi() {
        GitHubRepoDto repo = gitHubClient.fetchRepo("octocat", "Hello-World", null);

        assertNotNull(repo);
        assertEquals("Hello-World", repo.name());
        assertEquals("octocat/Hello-World", repo.fullName());
        assertFalse(repo.isPrivate());
        assertNotNull(repo.defaultBranch());
    }

    @Test
    void testFetchNonExistentRepoThrowsNotFound() {
        GitHubClient.GitHubClientException ex = assertThrows(
                GitHubClient.GitHubClientException.class,
                () -> gitHubClient.fetchRepo("archlens-non-existent-user-xyz", "non-existent-repo-12345", null)
        );

        assertEquals(404, ex.getStatusCode());
    }
}
