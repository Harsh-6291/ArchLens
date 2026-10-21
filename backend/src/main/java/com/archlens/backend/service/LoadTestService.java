package com.archlens.backend.service;

import com.archlens.backend.dto.CreateLoadTestRequest;
import com.archlens.backend.dto.LoadTestResponse;
import com.archlens.backend.dto.LoadTestResultResponse;
import com.archlens.backend.entity.ApiDefinition;
import com.archlens.backend.entity.Environment;
import com.archlens.backend.entity.LoadTest;
import com.archlens.backend.entity.LoadTestResult;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.entity.User;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.repository.ApiDefinitionRepository;
import com.archlens.backend.repository.EnvironmentRepository;
import com.archlens.backend.repository.LoadTestRepository;
import com.archlens.backend.repository.LoadTestResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Service
public class LoadTestService {

    private static final Logger log = LoggerFactory.getLogger(LoadTestService.class);

    private final LoadTestRepository loadTestRepository;
    private final LoadTestResultRepository loadTestResultRepository;
    private final ApiDefinitionRepository apiDefinitionRepository;
    private final EnvironmentRepository environmentRepository;
    private final EnvironmentService environmentService;
    private final HttpClient httpClient;

    @org.springframework.beans.factory.annotation.Autowired
    public LoadTestService(
            LoadTestRepository loadTestRepository,
            LoadTestResultRepository loadTestResultRepository,
            ApiDefinitionRepository apiDefinitionRepository,
            EnvironmentRepository environmentRepository,
            EnvironmentService environmentService
    ) {
        this(loadTestRepository, loadTestResultRepository, apiDefinitionRepository,
             environmentRepository, environmentService,
             HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
    }

    public LoadTestService(
            LoadTestRepository loadTestRepository,
            LoadTestResultRepository loadTestResultRepository,
            ApiDefinitionRepository apiDefinitionRepository,
            EnvironmentRepository environmentRepository,
            EnvironmentService environmentService,
            HttpClient httpClient
    ) {
        this.loadTestRepository = loadTestRepository;
        this.loadTestResultRepository = loadTestResultRepository;
        this.apiDefinitionRepository = apiDefinitionRepository;
        this.environmentRepository = environmentRepository;
        this.environmentService = environmentService;
        this.httpClient = httpClient;
    }

    // ------------------------------------------------------------- Create & Launch

    public LoadTestResponse createAndRun(UUID apiDefinitionId, CreateLoadTestRequest req, Optional<User> currentUser) {
        ApiDefinition apiDef = apiDefinitionRepository.findWithGraphById(apiDefinitionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "api_definition_not_found", "API definition not found"));

        verifyRepositoryAccess(apiDef.getGraph().getRepository(), currentUser);

        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "validation_error", "'name' must not be blank");
        }

        Environment env = null;
        if (req.environmentId() != null) {
            env = environmentRepository.findById(req.environmentId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "environment_not_found", "Environment not found"));
        }

        int vus = (req.virtualUsers() != null && req.virtualUsers() > 0) ? Math.min(req.virtualUsers(), 100) : 10;
        int duration = (req.durationSeconds() != null && req.durationSeconds() > 0) ? Math.min(req.durationSeconds(), 120) : 30;
        int rampUp = (req.rampUpSeconds() != null && req.rampUpSeconds() >= 0) ? Math.min(req.rampUpSeconds(), duration) : 5;

        LoadTest loadTest = new LoadTest(
                apiDef,
                env,
                req.name(),
                req.loadProfile(),
                vus,
                duration,
                rampUp
        );
        loadTest.setStatus("RUNNING");
        loadTest.setStartedAt(Instant.now());
        loadTest = loadTestRepository.save(loadTest);

        // Execute load test
        executeLoadSimulation(loadTest, apiDef, env, currentUser.orElse(null));

        return LoadTestResponse.from(loadTest);
    }

    // ----------------------------------------------------------------- Getters

    @Transactional(readOnly = true)
    public List<LoadTestResponse> listForApi(UUID apiDefinitionId, Optional<User> currentUser) {
        ApiDefinition apiDef = apiDefinitionRepository.findWithGraphById(apiDefinitionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "api_definition_not_found", "API definition not found"));

        verifyRepositoryAccess(apiDef.getGraph().getRepository(), currentUser);

        return loadTestRepository.findByApiDefinitionIdOrderByCreatedAtDesc(apiDef.getId())
                .stream()
                .map(LoadTestResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public LoadTestResponse get(UUID loadTestId, Optional<User> currentUser) {
        LoadTest lt = loadTestRepository.findWithDetailsById(loadTestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "load_test_not_found", "Load test not found"));

        verifyRepositoryAccess(lt.getApiDefinition().getGraph().getRepository(), currentUser);
        return LoadTestResponse.from(lt);
    }

    @Transactional(readOnly = true)
    public LoadTestResultResponse getResult(UUID loadTestId, Optional<User> currentUser) {
        LoadTest lt = loadTestRepository.findWithDetailsById(loadTestId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "load_test_not_found", "Load test not found"));

        verifyRepositoryAccess(lt.getApiDefinition().getGraph().getRepository(), currentUser);

        LoadTestResult result = loadTestResultRepository.findByLoadTestId(lt.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "load_test_result_not_found", "Results not yet available for this load test"));

        return LoadTestResultResponse.from(result);
    }

    // ------------------------------------------------------------- Simulation Engine

    public void executeLoadSimulation(LoadTest loadTest, ApiDefinition apiDef, Environment env, User user) {
        Map<String, String> envVars = env != null
                ? environmentService.getResolvedVariables(env.getId(), user)
                : Map.of();

        String targetUrl = resolveTargetUrl(apiDef.getPath(), envVars);

        List<Integer> latencies = new CopyOnWriteArrayList<>();
        Map<String, Integer> statusCodes = new ConcurrentHashMap<>();
        int totalRequests = Math.max(loadTest.getVirtualUsers() * 3, 10);

        long startTime = System.currentTimeMillis();

        ExecutorService pool = Executors.newFixedThreadPool(Math.min(loadTest.getVirtualUsers(), 10));
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 0; i < totalRequests; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                long reqStart = System.currentTimeMillis();
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(targetUrl))
                            .timeout(Duration.ofSeconds(3))
                            .method(apiDef.getHttpMethod(), HttpRequest.BodyPublishers.noBody())
                            .build();

                    HttpResponse<Void> resp = httpClient.send(req, HttpResponse.BodyHandlers.discarding());
                    int lat = (int) (System.currentTimeMillis() - reqStart);
                    latencies.add(lat);
                    statusCodes.merge(String.valueOf(resp.statusCode()), 1, Integer::sum);
                } catch (Exception e) {
                    int lat = (int) (System.currentTimeMillis() - reqStart);
                    latencies.add(lat);
                    statusCodes.merge("502", 1, Integer::sum);
                }
            }, pool));
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Load test simulation timed out or was interrupted: {}", e.getMessage());
        } finally {
            pool.shutdownNow();
        }

        long durationMs = Math.max(System.currentTimeMillis() - startTime, 1);
        double rps = (totalRequests * 1000.0) / durationMs;

        // Compute metrics
        Collections.sort(latencies);
        int p50 = percentile(latencies, 0.50);
        int p90 = percentile(latencies, 0.90);
        int p95 = percentile(latencies, 0.95);
        int p99 = percentile(latencies, 0.99);
        int min = latencies.isEmpty() ? 0 : latencies.get(0);
        int max = latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1);
        int avg = latencies.isEmpty() ? 0 : (int) latencies.stream().mapToInt(Integer::intValue).average().orElse(0);

        int failed = statusCodes.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("2"))
                .mapToInt(Map.Entry::getValue)
                .sum();
        int success = totalRequests - failed;
        double errorRate = (totalRequests > 0) ? ((double) failed / totalRequests) : 0.0;

        // Threshold detection (M3.3)
        String threshold = "SAFE";
        String bottleneck = "System operated within latency and stability parameters.";

        if (errorRate > 0.15 || p95 > 1500) {
            threshold = "SATURATED";
            bottleneck = "Saturation reached: high error rate (" + String.format("%.1f%%", errorRate * 100) + ") and severe latency degradation under concurrency.";
        } else if (errorRate > 0.03 || p90 > 500) {
            threshold = "WARNING";
            bottleneck = "Latency inflection point detected at higher concurrency (" + p90 + "ms p90). Potential resource contention or database pool constraint.";
        }

        // Persist result
        LoadTestResult result = new LoadTestResult(
                loadTest,
                totalRequests,
                success,
                failed,
                rps,
                p50, p90, p95, p99,
                min, max, avg,
                errorRate,
                statusCodes,
                threshold,
                bottleneck
        );
        loadTestResultRepository.save(result);

        loadTest.setStatus("COMPLETED");
        loadTest.setCompletedAt(Instant.now());
        loadTestRepository.save(loadTest);
    }

    private int percentile(List<Integer> sorted, double pct) {
        if (sorted.isEmpty()) return 0;
        int idx = (int) Math.ceil(pct * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    private String resolveTargetUrl(String pathTemplate, Map<String, String> envVars) {
        String path = pathTemplate != null ? pathTemplate : "/";
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path;
        }
        String baseUrl = envVars.getOrDefault("BASE_URL", envVars.getOrDefault("baseUrl", "http://localhost:8080"));
        if (baseUrl.endsWith("/") && path.startsWith("/")) {
            return baseUrl + path.substring(1);
        } else if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
            return baseUrl + "/" + path;
        }
        return baseUrl + path;
    }

    private void verifyRepositoryAccess(Repository repo, Optional<User> currentUser) {
        if ("user_oauth".equals(repo.getAccessMethod())) {
            if (currentUser.isEmpty()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required");
            }
            if (repo.getUser() != null && !repo.getUser().getId().equals(currentUser.get().getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "not_owner", "You do not have permission to access this resource");
            }
        }
    }
}
