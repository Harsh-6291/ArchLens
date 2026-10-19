package com.archlens.backend.controller;

import com.archlens.backend.client.GitHubClient;
import com.archlens.backend.config.RabbitMQConfig;
import com.archlens.backend.dto.CommitDto;
import com.archlens.backend.dto.CreateAnalysisJobRequest;
import com.archlens.backend.entity.AnalysisJob;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.entity.User;
import com.archlens.backend.messaging.AnalysisJobConsumer;
import com.archlens.backend.messaging.AnalysisJobMessage;
import com.archlens.backend.messaging.AnalysisJobProducer;
import com.archlens.backend.repository.AnalysisJobRepository;
import com.archlens.backend.repository.RepoRepository;
import com.archlens.backend.repository.UserRepository;
import com.archlens.backend.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AnalysisJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RepoRepository repoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AnalysisJobRepository analysisJobRepository;

    @Autowired
    private AnalysisJobConsumer consumer;

    @Autowired
    private AnalysisJobProducer producer;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private GitHubClient gitHubClient;

    @BeforeEach
    void setUp() {
        consumer.getReceivedMessages().clear();
        analysisJobRepository.deleteAll();
        repoRepository.deleteAll();
        userRepository.deleteAll();

        // Drain any stale messages from queues
        while (rabbitTemplate.receive(RabbitMQConfig.ANALYSIS_JOBS_QUEUE, 100) != null) {}
        while (rabbitTemplate.receive(RabbitMQConfig.ANALYSIS_JOBS_DLQ, 100) != null) {}
    }

    @Test
    void testCreateAnalysisJob_PublicRepo_Success_TransitionsToSucceeded() throws Exception {
        Repository repo = new Repository();
        repo.setGithubRepoId(1296269L);
        repo.setFullName("octocat/Hello-World");
        repo.setDefaultBranch("master");
        repo.setAccessMethod("public_system_token");
        repo = repoRepository.save(repo);

        CreateAnalysisJobRequest request = new CreateAnalysisJobRequest(repo.getId(), "master", "c0ffee1234");

        MvcResult result = mockMvc.perform(post("/api/v1/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.status", is("queued")))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        UUID jobId = UUID.fromString(objectMapper.readTree(responseBody).get("jobId").asText());

        // Wait for async consumer to process message
        AnalysisJobMessage received = consumer.getReceivedMessages().poll(10, TimeUnit.SECONDS);
        assertNotNull(received, "Consumer should have received analysis job message from RabbitMQ");
        assertEquals(jobId, received.jobId());
        assertEquals("c0ffee1234", received.commitSha());

        // Poll status endpoint
        mockMvc.perform(get("/api/v1/analysis/" + jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId", is(jobId.toString())))
                .andExpect(jsonPath("$.status", is("succeeded")))
                .andExpect(jsonPath("$.progress.percent", is(100)))
                .andExpect(jsonPath("$.progress.stage", is("completed")));
    }

    @Test
    void testCreateAnalysisJob_DefaultCommitSha_ResolvesLatestCommit() throws Exception {
        Repository repo = new Repository();
        repo.setGithubRepoId(1296269L);
        repo.setFullName("octocat/Hello-World");
        repo.setDefaultBranch("master");
        repo.setAccessMethod("public_system_token");
        repo = repoRepository.save(repo);

        List<CommitDto> commits = List.of(
                new CommitDto("resolvedSha123456", "Initial commit", "2026-10-20T00:00:00Z")
        );
        when(gitHubClient.fetchCommits(eq("octocat"), eq("Hello-World"), eq("master"), any())).thenReturn(commits);

        // Omit commitSha in request
        CreateAnalysisJobRequest request = new CreateAnalysisJobRequest(repo.getId(), "master", null);

        MvcResult result = mockMvc.perform(post("/api/v1/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.status", is("queued")))
                .andReturn();

        AnalysisJobMessage received = consumer.getReceivedMessages().poll(10, TimeUnit.SECONDS);
        assertNotNull(received);
        assertEquals("resolvedSha123456", received.commitSha());
    }

    @Test
    void testCreateAnalysisJob_ConflictIfAlreadyRunning_Returns409() throws Exception {
        Repository repo = new Repository();
        repo.setGithubRepoId(55555L);
        repo.setFullName("octocat/conflict-repo");
        repo.setDefaultBranch("main");
        repo.setAccessMethod("public_system_token");
        repo = repoRepository.save(repo);

        // Pre-create an in-flight job
        AnalysisJob existingJob = new AnalysisJob(repo, "main", "dupSha789");
        existingJob.setStatus("running");
        existingJob.setStartedAt(Instant.now());
        analysisJobRepository.save(existingJob);

        CreateAnalysisJobRequest request = new CreateAnalysisJobRequest(repo.getId(), "main", "dupSha789");

        mockMvc.perform(post("/api/v1/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("analysis_already_running")));
    }

    @Test
    void testCreateAnalysisJob_RepoNotFound_Returns404() throws Exception {
        CreateAnalysisJobRequest request = new CreateAnalysisJobRequest(UUID.randomUUID(), "main", "anySha");

        mockMvc.perform(post("/api/v1/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("repo_not_found")));
    }

    @Test
    void testCreateAnalysisJob_PrivateRepo_RequiresAuth() throws Exception {
        Repository repo = new Repository();
        repo.setGithubRepoId(99999L);
        repo.setFullName("private-org/secret-repo");
        repo.setDefaultBranch("main");
        repo.setAccessMethod("user_oauth");
        repo = repoRepository.save(repo);

        CreateAnalysisJobRequest request = new CreateAnalysisJobRequest(repo.getId(), "main", "secretSha");

        // No auth cookie provided
        mockMvc.perform(post("/api/v1/analysis")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("unauthorized")));
    }

    @Test
    void testCreateAnalysisJob_PrivateRepo_ForbiddenForNonOwner() throws Exception {
        User owner = new User();
        owner.setGithubId(111L);
        owner.setGithubUsername("ownerUser");
        owner = userRepository.save(owner);

        User attacker = new User();
        attacker.setGithubId(222L);
        attacker.setGithubUsername("attackerUser");
        attacker = userRepository.save(attacker);

        Repository repo = new Repository();
        repo.setUser(owner);
        repo.setGithubRepoId(88888L);
        repo.setFullName("ownerUser/secret-repo");
        repo.setDefaultBranch("main");
        repo.setAccessMethod("user_oauth");
        repo = repoRepository.save(repo);

        String attackerToken = jwtService.generateSessionToken(attacker);
        CreateAnalysisJobRequest request = new CreateAnalysisJobRequest(repo.getId(), "main", "secretSha");

        mockMvc.perform(post("/api/v1/analysis")
                        .cookie(new Cookie("archlens_session", attackerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("not_owner")));
    }

    @Test
    void testGetAnalysisJobStatus_NotFound_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/analysis/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code", is("job_not_found")));
    }

    @Test
    void testDeadLetterRoutingAndFailedJobState() throws Exception {
        Repository repo = new Repository();
        repo.setGithubRepoId(77777L);
        repo.setFullName("octocat/fail-repo");
        repo.setDefaultBranch("main");
        repo.setAccessMethod("public_system_token");
        repo = repoRepository.save(repo);

        AnalysisJob job = new AnalysisJob(repo, "main", "failSha123");
        job = analysisJobRepository.save(job);

        // Send a message flagged to fail during consumption
        AnalysisJobMessage failingMessage = new AnalysisJobMessage(
                job.getId(),
                repo.getId(),
                "https://github.com/" + repo.getFullName(),
                job.getCommitSha(),
                repo.getAccessMethod(),
                true
        );
        producer.sendAnalysisJob(failingMessage);

        // Message should land on dead-letter queue
        Object dlqObject = rabbitTemplate.receiveAndConvert(RabbitMQConfig.ANALYSIS_JOBS_DLQ, 10000);
        assertNotNull(dlqObject, "Failed job should be routed to dead-letter queue");
        assertTrue(dlqObject instanceof AnalysisJobMessage);
        AnalysisJobMessage dlqMessage = (AnalysisJobMessage) dlqObject;
        assertEquals(job.getId(), dlqMessage.jobId());

        // Verify job record in database reflects failure
        AnalysisJob updatedJob = analysisJobRepository.findById(job.getId()).orElseThrow();
        assertEquals("failed", updatedJob.getStatus());
        assertNotNull(updatedJob.getErrorMessage());
        assertTrue(updatedJob.getErrorMessage().contains("failed intentionally"));
    }
}
