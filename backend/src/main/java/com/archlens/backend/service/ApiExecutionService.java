package com.archlens.backend.service;

import com.archlens.backend.dto.ExecuteApiRequest;
import com.archlens.backend.dto.ExecuteApiResponse;
import com.archlens.backend.entity.ApiDefinition;
import com.archlens.backend.entity.Repository;
import com.archlens.backend.entity.RequestExecution;
import com.archlens.backend.entity.User;
import com.archlens.backend.exception.ApiException;
import com.archlens.backend.repository.ApiDefinitionRepository;
import com.archlens.backend.repository.RequestExecutionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class ApiExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ApiExecutionService.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final int MAX_BODY_EXCERPT_CHARS = 65536;

    private final ApiDefinitionRepository apiDefinitionRepository;
    private final RequestExecutionRepository requestExecutionRepository;
    private final EnvironmentService environmentService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @org.springframework.beans.factory.annotation.Autowired
    public ApiExecutionService(
            ApiDefinitionRepository apiDefinitionRepository,
            RequestExecutionRepository requestExecutionRepository,
            EnvironmentService environmentService,
            ObjectMapper objectMapper
    ) {
        this(apiDefinitionRepository, requestExecutionRepository, environmentService, objectMapper,
                HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT).build());
    }

    public ApiExecutionService(
            ApiDefinitionRepository apiDefinitionRepository,
            RequestExecutionRepository requestExecutionRepository,
            EnvironmentService environmentService,
            ObjectMapper objectMapper,
            HttpClient httpClient
    ) {
        this.apiDefinitionRepository = apiDefinitionRepository;
        this.requestExecutionRepository = requestExecutionRepository;
        this.environmentService = environmentService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public ExecuteApiResponse execute(UUID apiDefinitionId, ExecuteApiRequest req, Optional<User> currentUser) {
        ApiDefinition apiDef = apiDefinitionRepository.findWithGraphById(apiDefinitionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "api_definition_not_found", "API definition not found"));

        verifyRepositoryAccess(apiDef.getGraph().getRepository(), currentUser);

        // 1. Resolve environment variables if an environment was supplied
        Map<String, String> envVars = new HashMap<>();
        if (req != null && req.environmentId() != null) {
            envVars = environmentService.getResolvedVariables(req.environmentId(), currentUser.orElse(null));
        }

        // 2. Resolve target URL with variable and path parameter substitution
        String targetUrl = buildTargetUrl(apiDef.getPath(), req, envVars);

        // 3. Build HTTP Request
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(REQUEST_TIMEOUT);

        String method = apiDef.getHttpMethod().toUpperCase(Locale.ROOT);
        boolean hasBody = req != null && req.body() != null;
        String bodyString = hasBody ? serializeBody(req.body(), envVars) : null;

        HttpRequest.BodyPublisher bodyPublisher = bodyString != null
                ? HttpRequest.BodyPublishers.ofString(bodyString, StandardCharsets.UTF_8)
                : HttpRequest.BodyPublishers.noBody();

        switch (method) {
            case "GET" -> requestBuilder.GET();
            case "DELETE" -> requestBuilder.DELETE();
            case "POST" -> requestBuilder.POST(bodyPublisher);
            case "PUT" -> requestBuilder.PUT(bodyPublisher);
            case "PATCH" -> requestBuilder.method("PATCH", bodyPublisher);
            default -> requestBuilder.method(method, bodyPublisher);
        }

        // Apply headers with variable substitution
        boolean hasContentType = false;
        if (req != null && req.headers() != null) {
            for (Map.Entry<String, String> header : req.headers().entrySet()) {
                String headerName = header.getKey();
                if (isRestrictedHeader(headerName)) continue;

                String headerValue = substituteVariables(header.getValue(), envVars);
                requestBuilder.header(headerName, headerValue);
                if ("content-type".equalsIgnoreCase(headerName)) {
                    hasContentType = true;
                }
            }
        }
        if (hasBody && !hasContentType) {
            requestBuilder.header("Content-Type", "application/json");
        }

        // 4. Send HTTP Request and record telemetry
        long startTime = System.currentTimeMillis();
        try {
            HttpResponse<String> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int latencyMs = (int) (System.currentTimeMillis() - startTime);

            int statusCode = response.statusCode();
            String responseBody = response.body();
            Map<String, String> flatHeaders = flattenHeaders(response.headers());
            String headersJson = objectMapper.writeValueAsString(flatHeaders);

            String excerpt = responseBody != null && responseBody.length() > MAX_BODY_EXCERPT_CHARS
                    ? responseBody.substring(0, MAX_BODY_EXCERPT_CHARS)
                    : responseBody;

            RequestExecution execution = new RequestExecution(
                    apiDef, null, statusCode, latencyMs, headersJson, excerpt, null
            );
            execution = requestExecutionRepository.save(execution);

            Object parsedResponseBody = parseResponseBody(responseBody);
            return new ExecuteApiResponse(
                    execution.getId(),
                    statusCode,
                    latencyMs,
                    flatHeaders,
                    parsedResponseBody,
                    null
            );

        } catch (HttpConnectTimeoutException | ConnectException e) {
            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            persistFailure(apiDef, 502, latencyMs, "upstream_unreachable: " + e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "upstream_unreachable",
                    "Target host unreachable: " + e.getMessage());

        } catch (HttpTimeoutException e) {
            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            persistFailure(apiDef, 504, latencyMs, "upstream_timeout");
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "upstream_timeout",
                    "Upstream server timed out after " + REQUEST_TIMEOUT.toSeconds() + "s");

        } catch (IOException e) {
            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            persistFailure(apiDef, 502, latencyMs, "upstream_error: " + e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "upstream_unreachable",
                    "Upstream connection failed: " + e.getMessage());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "execution_interrupted", "Request was interrupted");
        } catch (Exception e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "execution_failed", e.getMessage());
        }
    }

    /**
     * Persists a failed execution record. Called after throwing, so it runs
     * in its own auto-committed repository transaction.
     */
    private void persistFailure(ApiDefinition apiDef, int statusCode, int latencyMs, String error) {
        RequestExecution execution = new RequestExecution(apiDef, null, statusCode, latencyMs, "{}", null, error);
        requestExecutionRepository.save(execution);
    }

    private String buildTargetUrl(String pathTemplate, ExecuteApiRequest req, Map<String, String> envVars) {
        String path = pathTemplate != null ? pathTemplate : "/";

        // Substitute path parameters: {id} -> value
        if (req != null && req.pathParams() != null) {
            for (Map.Entry<String, String> entry : req.pathParams().entrySet()) {
                String val = substituteVariables(entry.getValue(), envVars);
                path = path.replace("{" + entry.getKey() + "}", URLEncoder.encode(val, StandardCharsets.UTF_8));
            }
        }

        // Substitute variables in path
        path = substituteVariables(path, envVars);

        String fullUrl;
        if (path.startsWith("http://") || path.startsWith("https://")) {
            fullUrl = path;
        } else {
            String baseUrl = envVars.getOrDefault("BASE_URL", envVars.getOrDefault("baseUrl", "http://localhost:8080"));
            if (baseUrl.endsWith("/") && path.startsWith("/")) {
                fullUrl = baseUrl + path.substring(1);
            } else if (!baseUrl.endsWith("/") && !path.startsWith("/")) {
                fullUrl = baseUrl + "/" + path;
            } else {
                fullUrl = baseUrl + path;
            }
        }

        // Append query parameters
        if (req != null && req.queryParams() != null && !req.queryParams().isEmpty()) {
            StringJoiner joiner = new StringJoiner("&");
            for (Map.Entry<String, String> qp : req.queryParams().entrySet()) {
                String val = substituteVariables(qp.getValue(), envVars);
                joiner.add(URLEncoder.encode(qp.getKey(), StandardCharsets.UTF_8) + "=" +
                        URLEncoder.encode(val, StandardCharsets.UTF_8));
            }
            fullUrl += (fullUrl.contains("?") ? "&" : "?") + joiner;
        }

        return fullUrl;
    }

    private String substituteVariables(String input, Map<String, String> vars) {
        if (input == null || vars == null || vars.isEmpty()) return input;
        String res = input;
        for (Map.Entry<String, String> v : vars.entrySet()) {
            res = res.replace("{{" + v.getKey() + "}}", v.getValue());
        }
        return res;
    }

    private String serializeBody(Object body, Map<String, String> envVars) {
        if (body instanceof String s) {
            return substituteVariables(s, envVars);
        }
        try {
            String json = objectMapper.writeValueAsString(body);
            return substituteVariables(json, envVars);
        } catch (Exception e) {
            return String.valueOf(body);
        }
    }

    private Object parseResponseBody(String body) {
        if (body == null || body.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception e) {
            return body;
        }
    }

    private Map<String, String> flattenHeaders(HttpHeaders headers) {
        Map<String, String> map = new LinkedHashMap<>();
        headers.map().forEach((k, v) -> {
            if (k != null && v != null && !v.isEmpty()) {
                map.put(k, String.join(", ", v));
            }
        });
        return map;
    }

    private boolean isRestrictedHeader(String name) {
        if (name == null) return true;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.equals("content-length") || lower.equals("host") || lower.equals("connection");
    }

    public org.springframework.data.domain.Page<com.archlens.backend.dto.RequestExecutionResponse> getExecutions(
            UUID apiDefinitionId,
            org.springframework.data.domain.Pageable pageable,
            Optional<User> currentUser
    ) {
        ApiDefinition apiDef = apiDefinitionRepository.findWithGraphById(apiDefinitionId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "api_definition_not_found", "API definition not found"));

        verifyRepositoryAccess(apiDef.getGraph().getRepository(), currentUser);

        return requestExecutionRepository.findByApiDefinitionIdOrderByExecutedAtDesc(apiDef.getId(), pageable)
                .map(com.archlens.backend.dto.RequestExecutionResponse::from);
    }

    private void verifyRepositoryAccess(Repository repo, Optional<User> currentUser) {
        if ("user_oauth".equals(repo.getAccessMethod())) {
            if (currentUser.isEmpty()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "unauthorized", "Authentication required to execute this API");
            }
            if (repo.getUser() != null && !repo.getUser().getId().equals(currentUser.get().getId())) {
                throw new ApiException(HttpStatus.FORBIDDEN, "not_owner", "You do not have permission to execute this API");
            }
        }
    }
}

