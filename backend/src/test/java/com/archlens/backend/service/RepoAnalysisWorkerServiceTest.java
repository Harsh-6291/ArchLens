package com.archlens.backend.service;

import com.archlens.backend.client.GitHubClient;
import com.archlens.backend.entity.GithubCredential;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.entity.User;
import com.archlens.backend.repository.GithubCredentialRepository;
import com.archlens.backend.repository.RepoRepository;
import com.archlens.backend.repository.UserRepository;
import com.archlens.backend.security.EncryptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.*;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
class RepoAnalysisWorkerServiceTest {

    @Autowired
    private RepoAnalysisWorkerService workerService;

    @Autowired
    private RepoRepository repoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GithubCredentialRepository credentialRepository;

    @Autowired
    private EncryptionService encryptionService;

    @MockBean
    private GitHubClient gitHubClient;

    @TempDir
    Path tempWorkspaceDir;

    @BeforeEach
    void setUp() throws Exception {
        credentialRepository.deleteAll();
        repoRepository.deleteAll();
        userRepository.deleteAll();

        // Override workspace dir to the TempDir
        var field = RepoAnalysisWorkerService.class.getDeclaredField("workspaceBaseDir");
        field.setAccessible(true);
        field.set(workerService, tempWorkspaceDir.toString());
    }

    @AfterEach
    void tearDown() {
        credentialRepository.deleteAll();
        repoRepository.deleteAll();
        userRepository.deleteAll();
    }

    // -----------------------------------------------------------------------
    // Helper: build a minimal .tar.gz in memory
    // -----------------------------------------------------------------------
    private byte[] buildTarGz(String rootDir, String... filePaths) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos);
             TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {
            taos.setLongFileMode(LONGFILE_POSIX);

            // root directory entry (GitHub wraps everything in a root dir)
            TarArchiveEntry rootEntry = new TarArchiveEntry(rootDir + "/");
            taos.putArchiveEntry(rootEntry);
            taos.closeArchiveEntry();

            for (String relPath : filePaths) {
                String content = "# fixture file: " + relPath;
                byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry fileEntry = new TarArchiveEntry(rootDir + "/" + relPath);
                fileEntry.setSize(contentBytes.length);
                taos.putArchiveEntry(fileEntry);
                taos.write(contentBytes);
                taos.closeArchiveEntry();
            }
        }
        return bos.toByteArray();
    }

    // -----------------------------------------------------------------------
    // Test 1: public repo (system-token) — tarball fetched and extracted
    // -----------------------------------------------------------------------
    @Test
    void testFetchRepoContents_PublicRepo_ExtractsTarball() throws Exception {
        Repository repo = new Repository();
        repo.setGithubRepoId(1L);
        repo.setFullName("octocat/hello-world");
        repo.setDefaultBranch("master");
        repo.setAccessMethod("public_system_token");
        repo = repoRepository.save(repo);

        byte[] tarball = buildTarGz("octocat-hello-world-c0ffee1",
                "README.md", "pom.xml", "src/main/java/App.java");

        when(gitHubClient.getDecryptedSystemToken()).thenReturn(java.util.Optional.of("sys-token-xyz"));
        when(gitHubClient.downloadTarball(eq("octocat"), eq("hello-world"), eq("c0ffee1"), eq("sys-token-xyz")))
                .thenReturn(tarball);

        Path workspace = workerService.fetchRepoContents(repo.getId(), "c0ffee1");

        assertNotNull(workspace);
        assertTrue(Files.exists(workspace));
        assertTrue(Files.exists(workspace.resolve("README.md")));
        assertTrue(Files.exists(workspace.resolve("pom.xml")));
        assertTrue(Files.exists(workspace.resolve("src/main/java/App.java")));

        verify(gitHubClient).downloadTarball("octocat", "hello-world", "c0ffee1", "sys-token-xyz");
    }

    // -----------------------------------------------------------------------
    // Test 2: private repo (user-oauth) — uses user's decrypted credential
    // -----------------------------------------------------------------------
    @Test
    void testFetchRepoContents_PrivateRepo_UsesUserOAuthToken() throws Exception {
        User owner = new User();
        owner.setGithubId(42L);
        owner.setGithubUsername("privateOwner");
        owner = userRepository.save(owner);

        String rawToken = "user-oauth-secret-token";
        byte[] encryptedToken = encryptionService.encrypt(rawToken);
        GithubCredential cred = new GithubCredential(owner, encryptedToken, new String[]{"repo"}, null);
        credentialRepository.save(cred);

        Repository repo = new Repository();
        repo.setUser(owner);
        repo.setGithubRepoId(2L);
        repo.setFullName("privateOwner/private-repo");
        repo.setDefaultBranch("main");
        repo.setAccessMethod("user_oauth");
        repo = repoRepository.save(repo);

        byte[] tarball = buildTarGz("privateOwner-private-repo-deadbeef",
                "README.md", "build.gradle", "src/main/java/Service.java");

        when(gitHubClient.downloadTarball(eq("privateOwner"), eq("private-repo"), eq("deadbeef"), eq(rawToken)))
                .thenReturn(tarball);

        Path workspace = workerService.fetchRepoContents(repo.getId(), "deadbeef");

        assertNotNull(workspace);
        assertTrue(Files.exists(workspace.resolve("README.md")));
        assertTrue(Files.exists(workspace.resolve("build.gradle")));
        assertTrue(Files.exists(workspace.resolve("src/main/java/Service.java")));

        verify(gitHubClient).downloadTarball("privateOwner", "private-repo", "deadbeef", rawToken);
        // System token must NOT have been used
        verify(gitHubClient, never()).getDecryptedSystemToken();
    }

    // -----------------------------------------------------------------------
    // Test 3: repo not found throws IllegalArgumentException
    // -----------------------------------------------------------------------
    @Test
    void testFetchRepoContents_RepoNotFound_ThrowsException() {
        assertThrows(IllegalArgumentException.class,
                () -> workerService.fetchRepoContents(UUID.randomUUID(), "anysha"));
    }

    // -----------------------------------------------------------------------
    // Test 4: cleanup removes workspace directory
    // -----------------------------------------------------------------------
    @Test
    void testCleanupWorkspace_RemovesDirectory() throws Exception {
        Path ws = tempWorkspaceDir.resolve("some-workspace");
        Files.createDirectories(ws);
        Files.writeString(ws.resolve("file.txt"), "content");

        assertTrue(Files.exists(ws));
        workerService.cleanupWorkspace(ws);
        assertFalse(Files.exists(ws));
    }

    // -----------------------------------------------------------------------
    // Test 5: ZipSlip protection — malicious tar entry rejected
    // -----------------------------------------------------------------------
    @Test
    void testExtractTarGz_ZipSlip_Rejected() throws Exception {
        // Build a tar.gz with a path-traversal entry
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzos = new GZIPOutputStream(bos);
             TarArchiveOutputStream taos = new TarArchiveOutputStream(gzos)) {
            taos.setLongFileMode(LONGFILE_POSIX);

            TarArchiveEntry rootEntry = new TarArchiveEntry("root-dir/");
            taos.putArchiveEntry(rootEntry);
            taos.closeArchiveEntry();

            byte[] payload = "evil content".getBytes(StandardCharsets.UTF_8);
            TarArchiveEntry evil = new TarArchiveEntry("root-dir/../../evil.txt");
            evil.setSize(payload.length);
            taos.putArchiveEntry(evil);
            taos.write(payload);
            taos.closeArchiveEntry();
        }

        TarballExtractor extractor = new TarballExtractor();
        Path dest = tempWorkspaceDir.resolve("zip-slip-test");
        Files.createDirectories(dest);

        assertThrows(IOException.class, () -> extractor.extractTarGz(bos.toByteArray(), dest));
    }
}
