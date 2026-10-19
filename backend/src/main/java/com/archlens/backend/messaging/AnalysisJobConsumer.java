package com.archlens.backend.messaging;

import com.archlens.backend.config.RabbitMQConfig;
import com.archlens.backend.service.AnalysisJobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Component
public class AnalysisJobConsumer {

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobConsumer.class);
    private static final BlockingQueue<AnalysisJobMessage> receivedMessages = new LinkedBlockingQueue<>();

    private final AnalysisJobService jobService;

    public AnalysisJobConsumer(AnalysisJobService jobService) {
        this.jobService = jobService;
    }

    @RabbitListener(queues = RabbitMQConfig.ANALYSIS_JOBS_QUEUE)
    public void consumeAnalysisJob(AnalysisJobMessage message) {
        log.info("Consuming analysis job: {}", message.jobId());

        jobService.updateJobProgress(message.jobId(), "running", "detecting_frameworks", 40, null);

        if (message.failForTesting()) {
            log.warn("Failing analysis job {} for testing / dead-letter queue routing", message.jobId());
            jobService.updateJobProgress(message.jobId(), "failed", "failed", null, "Processing failed intentionally for testing");
            throw new RuntimeException("Intentional failure to trigger dead-letter queue routing");
        }

        jobService.updateJobProgress(message.jobId(), "running", "assembling_graph", 80, null);
        jobService.updateJobProgress(message.jobId(), "succeeded", "completed", 100, null);

        receivedMessages.offer(message);
    }

    public BlockingQueue<AnalysisJobMessage> getReceivedMessages() {
        return receivedMessages;
    }
}
