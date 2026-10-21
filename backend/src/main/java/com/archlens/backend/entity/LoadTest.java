package com.archlens.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "load_tests")
public class LoadTest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "api_definition_id", nullable = false)
    private ApiDefinition apiDefinition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "environment_id")
    private Environment environment;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "load_profile", nullable = false)
    private String loadProfile = "RAMP_UP";

    @Column(name = "virtual_users", nullable = false)
    private Integer virtualUsers = 10;

    @Column(name = "duration_seconds", nullable = false)
    private Integer durationSeconds = 30;

    @Column(name = "ramp_up_seconds", nullable = false)
    private Integer rampUpSeconds = 5;

    @Column(name = "status", nullable = false)
    private String status = "PENDING";

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public LoadTest() {}

    public LoadTest(
            ApiDefinition apiDefinition,
            Environment environment,
            String name,
            String loadProfile,
            Integer virtualUsers,
            Integer durationSeconds,
            Integer rampUpSeconds
    ) {
        this.apiDefinition = apiDefinition;
        this.environment = environment;
        this.name = name;
        this.loadProfile = loadProfile != null ? loadProfile : "RAMP_UP";
        this.virtualUsers = virtualUsers != null ? virtualUsers : 10;
        this.durationSeconds = durationSeconds != null ? durationSeconds : 30;
        this.rampUpSeconds = rampUpSeconds != null ? rampUpSeconds : 5;
        this.status = "PENDING";
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public ApiDefinition getApiDefinition() { return apiDefinition; }
    public void setApiDefinition(ApiDefinition apiDefinition) { this.apiDefinition = apiDefinition; }
    public Environment getEnvironment() { return environment; }
    public void setEnvironment(Environment environment) { this.environment = environment; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLoadProfile() { return loadProfile; }
    public void setLoadProfile(String loadProfile) { this.loadProfile = loadProfile; }
    public Integer getVirtualUsers() { return virtualUsers; }
    public void setVirtualUsers(Integer virtualUsers) { this.virtualUsers = virtualUsers; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public Integer getRampUpSeconds() { return rampUpSeconds; }
    public void setRampUpSeconds(Integer rampUpSeconds) { this.rampUpSeconds = rampUpSeconds; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
