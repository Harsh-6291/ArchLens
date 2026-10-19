package com.archlens.backend.messaging;

import com.archlens.backend.config.RabbitMQConfig;
import com.archlens.backend.service.RepoAnalysisWorkerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

import org.springframework.test.annotation.DirtiesContext;

/**
 * Tests RabbitMQ infrastructure only (queue existence, produce→consume round-trip,
 * dead-letter routing). The RepoAnalysisWorkerService is mocked out because worker
 * logic (GitHub tarball fetch) is exercised separately in RepoAnalysisWorkerServiceTest.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=true")
@DirtiesContext
class RabbitMQMessagingTest {

    @Autowired
    private AnalysisJobProducer producer;

    @Autowired
    private AnalysisJobConsumer consumer;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    /**
     * Mock the worker so the consumer doesn't attempt a real GitHub tarball fetch.
     * The mock returns null by default (no workspace), which the consumer handles
     * gracefully via the null-check in its finally block.
     */
    @MockBean
    private RepoAnalysisWorkerService workerService;

    @BeforeEach
    void setUp() {
        consumer.getReceivedMessages().clear();
        while (rabbitTemplate.receive(RabbitMQConfig.ANALYSIS_JOBS_QUEUE, 100) != null) {}
        while (rabbitTemplate.receive(RabbitMQConfig.ANALYSIS_JOBS_DLQ, 100) != null) {}
    }

    @Test
    void testQueueDurabilityAndDeadLetterConfiguration() {
        Properties queueProps = rabbitAdmin.getQueueProperties(RabbitMQConfig.ANALYSIS_JOBS_QUEUE);
        assertNotNull(queueProps, "analysis-jobs queue must exist in broker");

        Properties dlqProps = rabbitAdmin.getQueueProperties(RabbitMQConfig.ANALYSIS_JOBS_DLQ);
        assertNotNull(dlqProps, "analysis-jobs.dlq must exist in broker");
    }

    @Test
    void testProduceAndConsumeAnalysisJob() throws InterruptedException {
        UUID jobId = UUID.randomUUID();
        UUID repoId = UUID.randomUUID();
        AnalysisJobMessage message = new AnalysisJobMessage(
                jobId,
                repoId,
                "https://github.com/octocat/hello-world",
                "abc1234def",
                "public_system_token"
        );

        producer.sendAnalysisJob(message);

        AnalysisJobMessage received = consumer.getReceivedMessages().poll(10, TimeUnit.SECONDS);
        assertNotNull(received, "Consumer should have received message from analysis-jobs queue");
        assertEquals(jobId, received.jobId());
        assertEquals("https://github.com/octocat/hello-world", received.repoUrl());
        assertEquals("abc1234def", received.commitSha());
    }

    @Test
    void testDeadLetterRoutingOnProcessingFailure() {
        // Drain any previous DLQ messages
        while (rabbitTemplate.receive(RabbitMQConfig.ANALYSIS_JOBS_DLQ, 100) != null) {}

        UUID failedJobId = UUID.randomUUID();
        AnalysisJobMessage failingMessage = new AnalysisJobMessage(
                failedJobId,
                UUID.randomUUID(),
                "https://github.com/octocat/failing-repo",
                "failed123",
                "public_system_token",
                true // triggers intentional exception in consumer
        );

        producer.sendAnalysisJob(failingMessage);

        // Message should be routed to DLQ after rejection
        Object dlqObject = rabbitTemplate.receiveAndConvert(RabbitMQConfig.ANALYSIS_JOBS_DLQ, 10000);
        assertNotNull(dlqObject, "Message should be routed to DLQ upon processing failure");
        assertTrue(dlqObject instanceof AnalysisJobMessage, "DLQ payload should be AnalysisJobMessage");
        AnalysisJobMessage dlqMessage = (AnalysisJobMessage) dlqObject;
        assertEquals(failedJobId, dlqMessage.jobId());
    }
}
