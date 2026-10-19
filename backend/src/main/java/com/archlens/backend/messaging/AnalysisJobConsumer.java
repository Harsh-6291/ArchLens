package com.archlens.backend.messaging;

import com.archlens.backend.config.RabbitMQConfig;
import com.archlens.backend.detector.Tier1DetectorService;
import com.archlens.backend.detector.model.DetectionResult;
import com.archlens.backend.service.AnalysisJobService;
import com.archlens.backend.service.RepoAnalysisWorkerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class AnalysisJobConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobConsumer.class);
    private static final BlockingQueue<AnalysisJobMessage> receivedMessages = new LinkedBlockingQueue<>();

    private final AnalysisJobService jobService;
    private final RepoAnalysisWorkerService workerService;
    private final Tier1DetectorService detectorService;

    public AnalysisJobConsumer(
            AnalysisJobService jobService,
            RepoAnalysisWorkerService workerService,
            Tier1DetectorService detectorService
    ) {
        this.jobService = jobService;
        this.workerService = workerService;
        this.detectorService = detectorService;
    }

    @RabbitListener(queues = RabbitMQConfig.ANALYSIS_JOBS_QUEUE)
    public void consumeAnalysisJob(AnalysisJobMessage message) {
        log.info("Consuming analysis job: jobId={}, repo={}, commitSha={}",
                message.jobId(), message.repoId(), message.commitSha());

        if (message.failForTesting()) {
            log.warn("Failing analysis job {} for testing / dead-letter queue routing", message.jobId());
            jobService.updateJobProgress(message.jobId(), "failed", "failed", null,
                    "Processing failed intentionally for testing");
            throw new RuntimeException("Intentional failure to trigger dead-letter queue routing");
        }

        Path workspace = null;
        try {
            // Stage 1: fetch repository contents via tarball API
            jobService.updateJobProgress(message.jobId(), "running", "fetching_contents", 10, null);
            workspace = workerService.fetchRepoContents(message.repoId(), message.commitSha());

            // Stage 2: detect frameworks/manifests (Tier 1 manifest detection)
            if (workspace != null) {
                DetectionResult detectionResult = detectorService.detect(workspace);
                String detectorResultsJson = detectorService.toJson(detectionResult);
                jobService.updateJobProgressAndResults(message.jobId(), "running", "detecting_frameworks", 40, detectorResultsJson, null);
            } else {
                jobService.updateJobProgress(message.jobId(), "running", "detecting_frameworks", 40, null);
            }

            // Stage 3: assemble graph (M1.9 will build real graph)
            jobService.updateJobProgress(message.jobId(), "running", "assembling_graph", 80, null);

            // Job complete
            jobService.updateJobProgress(message.jobId(), "succeeded", "completed", 100, null);

            receivedMessages.offer(message);
            log.info("Analysis job {} completed successfully", message.jobId());

        } catch (IOException e) {
            log.error("IO error during analysis job {}: {}", message.jobId(), e.getMessage(), e);
            jobService.updateJobProgress(message.jobId(), "failed", "failed", null,
                    "Failed to fetch repository contents: " + e.getMessage());
            throw new RuntimeException("Analysis job IO failure", e);

        } catch (Exception e) {
            log.error("Unexpected error during analysis job {}: {}", message.jobId(), e.getMessage(), e);
            jobService.updateJobProgress(message.jobId(), "failed", "failed", null, e.getMessage());
            throw e;

        } finally {
            if (workspace != null) {
                workerService.cleanupWorkspace(workspace);
            }
        }
    }

    public BlockingQueue<AnalysisJobMessage> getReceivedMessages() {
        return receivedMessages;
    }
}
