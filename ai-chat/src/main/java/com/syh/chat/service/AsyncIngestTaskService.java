package com.syh.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syh.chat.dto.IngestTaskResponse;
import com.syh.chat.dto.IngestTaskEvent;
import com.syh.chat.entity.IngestTask;
import com.syh.chat.entity.KnowledgeDocument;
import com.syh.chat.entity.OutboxEvent;
import com.syh.chat.repository.IngestTaskRepository;
import com.syh.chat.repository.KnowledgeDocumentRepository;
import com.syh.chat.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
public class AsyncIngestTaskService {

    private final KnowledgeDocumentRepository documentRepository;
    private final IngestTaskRepository taskRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final String streamKey;
    private final Path uploadDir;
    private final String queueType;
    private final String kafkaTopic;

    public AsyncIngestTaskService(
            KnowledgeDocumentRepository documentRepository,
            IngestTaskRepository taskRepository,
            StringRedisTemplate stringRedisTemplate,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            @Value("${app.ingest.stream-key:ingest:tasks}") String streamKey,
            @Value("${app.storage.upload-dir:./data/uploads}") String uploadDir,
            @Value("${app.ingest.queue:redis}") String queueType,
            @Value("${app.ingest.kafka.topic:ingest-tasks}") String kafkaTopic
    ) {
        this.documentRepository = documentRepository;
        this.taskRepository = taskRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.streamKey = streamKey;
        this.uploadDir = Path.of(uploadDir);
        this.queueType = queueType;
        this.kafkaTopic = kafkaTopic;
    }

    @Transactional
    public IngestTaskResponse submit(Long userId, MultipartFile file, String titleOverride) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件为空");
        }
        String originalName = file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename();
        String title = (titleOverride == null || titleOverride.isBlank()) ? originalName : titleOverride.trim();

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setUserId(userId);
        doc.setTitle(title);
        doc.setStatus("QUEUED");
        doc.setSegmentCount(0);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        doc = documentRepository.save(doc);

        String taskId = UUID.randomUUID().toString();
        String filePath = persistFile(taskId, file);

        IngestTask task = new IngestTask();
        task.setId(taskId);
        task.setUserId(userId);
        task.setDocumentId(doc.getId());
        task.setStatus("QUEUED");
        task.setProgress(0);
        task.setProcessedSegments(0);
        task.setTotalSegments(0);
        task.setFilePath(filePath);
        task.setAttemptCount(0);
        task.setNextRetryAt(null);
        task.setLastError(null);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);

        enqueue(taskId, userId, doc.getId(), filePath);

        return toResponse(task);
    }

    private void enqueue(String taskId, Long userId, Long documentId, String filePath) {
        if ("kafka".equalsIgnoreCase(queueType)) {
            try {
                IngestTaskEvent evt = new IngestTaskEvent(taskId, userId, documentId, filePath);
                String payload = objectMapper.writeValueAsString(evt);
                OutboxEvent outbox = new OutboxEvent();
                outbox.setId(UUID.randomUUID().toString());
                outbox.setTopic(kafkaTopic);
                outbox.setMessageKey(taskId);
                outbox.setPayload(payload);
                outbox.setStatus("NEW");
                outbox.setAttemptCount(0);
                outbox.setNextRetryAt(null);
                outbox.setLastError(null);
                outbox.setCreatedAt(LocalDateTime.now());
                outboxEventRepository.save(outbox);
            } catch (Exception e) {
                throw new IllegalStateException("任务入队失败");
            }
            return;
        }
        Map<String, String> payload = new HashMap<>();
        payload.put("taskId", String.valueOf(taskId));
        payload.put("userId", String.valueOf(userId));
        payload.put("documentId", String.valueOf(documentId));
        payload.put("filePath", String.valueOf(filePath));

        RecordId recordId = stringRedisTemplate.opsForStream().add(
                MapRecord.create(Objects.requireNonNull(streamKey), payload)
        );
        if (recordId == null) {
            throw new IllegalStateException("任务入队失败");
        }
    }

    public IngestTaskResponse get(Long userId, String taskId) {
        IngestTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在"));
        return toResponse(task);
    }

    private String persistFile(String taskId, MultipartFile file) {
        try {
            Files.createDirectories(uploadDir);
            Path target = uploadDir.resolve(taskId + ".pdf");
            Files.write(target, file.getBytes());
            return target.toAbsolutePath().toString();
        } catch (IOException e) {
            throw new IllegalStateException("文件保存失败");
        }
    }

    private IngestTaskResponse toResponse(IngestTask task) {
        return new IngestTaskResponse(
                task.getId(),
                task.getDocumentId(),
                task.getStatus(),
                task.getProgress(),
                task.getProcessedSegments(),
                task.getTotalSegments(),
                task.getErrorMessage(),
                task.getAttemptCount(),
                task.getNextRetryAt(),
                task.getLastError(),
                task.getUpdatedAt()
        );
    }
}
