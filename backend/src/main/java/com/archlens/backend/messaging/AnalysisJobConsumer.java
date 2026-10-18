package com.archlens.backend.messaging;

import com.archlens.backend.config.RabbitMQConfig;
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

    @RabbitListener(queues = RabbitMQConfig.ANALYSIS_JOBS_QUEUE)
    public void consumeAnalysisJob(AnalysisJobMessage message) {
        log.info("Received analysis job: {}", message.jobId());

        if (message.failForTesting()) {
            log.warn("Failing analysis job {} for dead-letter testing", message.jobId());
            throw new RuntimeException("Intentional failure to trigger dead-letter queue routing");
        }

        receivedMessages.offer(message);
    }

    public BlockingQueue<AnalysisJobMessage> getReceivedMessages() {
        return receivedMessages;
    }
}
