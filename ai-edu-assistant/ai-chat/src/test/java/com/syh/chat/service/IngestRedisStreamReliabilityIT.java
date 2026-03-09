package com.syh.chat.service;

import com.syh.chat.entity.IngestTask;
import com.syh.chat.entity.KnowledgeDocument;
import com.syh.chat.repository.IngestTaskRepository;
import com.syh.chat.repository.KnowledgeDocumentRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@SuppressWarnings("resource")
public class IngestRedisStreamReliabilityIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.36")
            .withDatabaseName("ollama_chat")
            .withUsername("root")
            .withPassword("1234");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

        registry.add("jwt.secret", () -> "test-secret-key-must-be-at-least-256-bits-long-for-hs256-please-change-this");
        registry.add("bigmodel.api-key", () -> "dummy");
        registry.add("siliconflow.api-key", () -> "dummy");
        registry.add("chroma.base-url", () -> "http://127.0.0.1:8000");

        registry.add("app.ingest.queue", () -> "redis");
        registry.add("app.ingest.stream-key", () -> "ingest:tasks:test");
        registry.add("app.ingest.group", () -> "ingest-workers-test");
        registry.add("app.ingest.redis.max-attempts", () -> "2");
        registry.add("app.ingest.redis.retry.base-backoff-ms", () -> "0");
        registry.add("app.ingest.redis.retry.max-backoff-ms", () -> "0");
        registry.add("app.ingest.redis.reclaim-idle-ms", () -> "0");
        registry.add("app.ingest.redis.reclaim-interval-ms", () -> "3600000");
        registry.add("app.ingest.redis.dlq-stream-key", () -> "ingest:tasks:dlq:test");
    }

    @Autowired
    KnowledgeDocumentRepository documentRepository;

    @Autowired
    IngestTaskRepository taskRepository;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Autowired
    IngestTaskPendingReclaimer reclaimer;

    @MockBean
    @SuppressWarnings("removal")
    KnowledgeIngestService knowledgeIngestService;

    @Test
    void failureLeadsToRetryThenDeadAndDlq() throws Exception {
        Mockito.when(knowledgeIngestService.ingestExistingDocumentFromFile(anyLong(), anyLong(), anyString(), any()))
                .thenThrow(new RuntimeException("boom"));

        Long userId = 1L;
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setUserId(userId);
        doc.setTitle("t");
        doc.setStatus("QUEUED");
        doc.setSegmentCount(0);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        doc = documentRepository.save(doc);

        String taskId = UUID.randomUUID().toString();
        IngestTask task = new IngestTask();
        task.setId(taskId);
        task.setUserId(userId);
        task.setDocumentId(doc.getId());
        task.setStatus("QUEUED");
        task.setProgress(0);
        task.setProcessedSegments(0);
        task.setTotalSegments(0);
        task.setFilePath("dummy");
        task.setErrorMessage(null);
        task.setAttemptCount(0);
        task.setNextRetryAt(null);
        task.setLastError(null);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);

        Map<String, String> payload = new HashMap<>();
        payload.put("taskId", taskId);
        payload.put("userId", String.valueOf(userId));
        payload.put("documentId", String.valueOf(doc.getId()));
        payload.put("filePath", "dummy");

        RecordId rid = stringRedisTemplate.opsForStream().add("ingest:tasks:test", payload);
        assertNotNull(rid);

        IngestTask afterFirst = awaitTask(taskId, "RETRYING", 50, 200);
        assertEquals(1, afterFirst.getAttemptCount());
        assertNotNull(afterFirst.getNextRetryAt());

        reclaimer.reclaimOnce();

        IngestTask afterDead = awaitTask(taskId, "DEAD", 50, 200);
        assertEquals(2, afterDead.getAttemptCount());

        Long dlqSize = stringRedisTemplate.opsForStream().size("ingest:tasks:dlq:test");
        assertNotNull(dlqSize);
        assertTrue(dlqSize >= 1);

        long pending = 0;
        try {
            org.springframework.data.redis.connection.stream.PendingMessagesSummary summary =
                    stringRedisTemplate.opsForStream().pending("ingest:tasks:test", "ingest-workers-test");
            pending = (summary == null) ? 0 : summary.getTotalPendingMessages();
        } catch (Exception ignored) {
        }
        assertEquals(0, pending);
    }

    private IngestTask awaitTask(String taskId, String status, int attempts, long sleepMs) throws InterruptedException {
        Objects.requireNonNull(taskId);
        Objects.requireNonNull(status);
        for (int i = 0; i < attempts; i++) {
            IngestTask t = taskRepository.findById(taskId).orElseThrow();
            if (status.equalsIgnoreCase(t.getStatus())) {
                return t;
            }
            Thread.sleep(sleepMs);
        }
        return taskRepository.findById(taskId).orElseThrow();
    }
}
