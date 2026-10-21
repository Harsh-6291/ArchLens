package com.archlens.backend.controller;

import com.archlens.backend.client.GitHubClient;
import com.archlens.backend.dto.GitHubPullRequestPayload;
import com.archlens.backend.entity.ArchitectureGraph;
import com.archlens.backend.entity.GraphNode;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.entity.User;
import com.archlens.backend.repository.ArchitectureGraphRepository;
import com.archlens.backend.repository.GitHubPrEventRepository;
import com.archlens.backend.repository.RepoRepository;
import com.archlens.backend.repository.UserRepository;
import com.archlens.backend.security.EncryptionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class GitHubWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RepoRepository repoRepository;

    @Autowired
    private ArchitectureGraphRepository graphRepository;

    @Autowired
    private GitHubPrEventRepository prEventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.archlens.backend.repository.AnalysisJobRepository analysisJobRepository;

    @MockBean
    private GitHubClient gitHubClient;

    private Repository testRepo;

    @BeforeEach
    void setUp() {
        prEventRepository.deleteAll();
        graphRepository.deleteAll();
        analysisJobRepository.deleteAll();
        repoRepository.deleteAll();
        userRepository.deleteAll();

        User user = new User(12345L, "pr-user", "pr@example.com", "https://avatar.url");
        user = userRepository.save(user);

        testRepo = new Repository(user, 999999L, "archlens/demo-service", "main", "system_token");
        testRepo = repoRepository.save(testRepo);

        com.archlens.backend.entity.AnalysisJob job = new com.archlens.backend.entity.AnalysisJob(testRepo, "main", "base-sha-1234");
        job = analysisJobRepository.save(job);

        ArchitectureGraph graph = new ArchitectureGraph(job, testRepo, "base-sha-1234");
        graph = graphRepository.save(graph);

        when(gitHubClient.createPrComment(anyString(), anyString(), anyInt(), anyString(), any()))
                .thenReturn(new GitHubClient.GitHubCommentDto(555123L, "Summary", "https://github.com/archlens/demo-service/pull/42#issuecomment-555123"));
    }

    @Test
    void testPullRequestWebhook_Success() throws Exception {
        GitHubPullRequestPayload.HeadCommit head = new GitHubPullRequestPayload.HeadCommit("head-sha-7890", "feature/new-api");
        GitHubPullRequestPayload.BaseCommit base = new GitHubPullRequestPayload.BaseCommit("base-sha-1234", "main");
        GitHubPullRequestPayload.PullRequestDetails pr = new GitHubPullRequestPayload.PullRequestDetails(
                101L, 42, "Add Payment Gateway", "open", head, base
        );
        GitHubPullRequestPayload.GitHubRepoDetails repoDetails = new GitHubPullRequestPayload.GitHubRepoDetails(
                999999L, "demo-service", "archlens/demo-service", "main"
        );
        GitHubPullRequestPayload payload = new GitHubPullRequestPayload("opened", 42, pr, repoDetails);

        String jsonPayload = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/webhooks/github/pr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "pull_request")
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prNumber", is(42)))
                .andExpect(jsonPath("$.action", is("opened")))
                .andExpect(jsonPath("$.status", is("commented")))
                .andExpect(jsonPath("$.commentId", is("555123")))
                .andExpect(jsonPath("$.summaryMarkdown", containsString("ArchLens — Architecture & Security Impact Analysis")));
    }

    @Test
    void testPullRequestWebhook_UntrackedRepo_Ignored() throws Exception {
        GitHubPullRequestPayload.HeadCommit head = new GitHubPullRequestPayload.HeadCommit("head-sha-9999", "feature/untracked");
        GitHubPullRequestPayload.BaseCommit base = new GitHubPullRequestPayload.BaseCommit("base-sha-0000", "main");
        GitHubPullRequestPayload.PullRequestDetails pr = new GitHubPullRequestPayload.PullRequestDetails(
                102L, 99, "Untracked PR", "open", head, base
        );
        GitHubPullRequestPayload.GitHubRepoDetails repoDetails = new GitHubPullRequestPayload.GitHubRepoDetails(
                111222L, "unknown-repo", "unknown-owner/unknown-repo", "main"
        );
        GitHubPullRequestPayload payload = new GitHubPullRequestPayload("opened", 99, pr, repoDetails);

        String jsonPayload = objectMapper.writeValueAsString(payload);

        mockMvc.perform(post("/api/v1/webhooks/github/pr")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "pull_request")
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prNumber", is(99)))
                .andExpect(jsonPath("$.status", is("ignored_untracked_repo")));
    }

    @Test
    void testGetRepoPrEvents_ReturnsList() throws Exception {
        // Trigger a webhook event first
        testPullRequestWebhook_Success();

        mockMvc.perform(get("/api/v1/repos/" + testRepo.getId() + "/pr-events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].prNumber", is(42)))
                .andExpect(jsonPath("$[0].action", is("opened")));
    }
}
