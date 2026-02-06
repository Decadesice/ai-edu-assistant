package com.syh.chat.service;

import com.syh.chat.entity.IngestTask;
import com.syh.chat.entity.KnowledgeDocument;
import com.syh.chat.repository.IngestTaskRepository;
import com.syh.chat.repository.KnowledgeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class IngestTaskProcessor {

    private final IngestTaskRepository taskRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeIngestService knowledgeIngestService;

    public IngestTaskProcessor(
            IngestTaskRepository taskRepository,
            KnowledgeDocumentRepository documentRepository,
            KnowledgeIngestService knowledgeIngestService
    ) {
        this.taskRepository = taskRepository;
        this.documentRepository = documentRepository;
        this.knowledgeIngestService = knowledgeIngestService;
    }

    @Transactional
    public void process(String taskId, Long userId, Long documentId, String filePath) {
        Optional<IngestTask> taskOpt = taskRepository.findById(taskId);
        if (taskOpt.isEmpty()) {
            return;
        }
        IngestTask task = taskOpt.get();
        if ("SUCCEEDED".equalsIgnoreCase(task.getStatus()) || "FAILED".equalsIgnoreCase(task.getStatus())) {
            return;
        }

        task.setStatus("RUNNING");
        task.setUpdatedAt(LocalDateTime.now());
        taskRepository.save(task);

        Optional<KnowledgeDocument> docOpt = documentRepository.findById(documentId);
        if (docOpt.isPresent()) {
            KnowledgeDocument doc = docOpt.get();
            doc.setStatus("PROCESSING");
            doc.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(doc);
        }

        AtomicReference<Instant> lastPersistRef = new AtomicReference<>(Instant.EPOCH);
        try {
            knowledgeIngestService.ingestExistingDocumentFromFile(userId, documentId, filePath, (processed, total) -> {
                task.setProcessedSegments(processed);
                task.setTotalSegments(total);
                int progress = total <= 0 ? 0 : Math.min(99, (processed * 100) / total);
                task.setProgress(progress);
                Instant now = Instant.now();
                Instant lastPersist = lastPersistRef.get();
                if (processed == total || processed % 5 == 0 || Duration.between(lastPersist, now).toMillis() > 1000) {
                    task.setUpdatedAt(LocalDateTime.now());
                    taskRepository.save(task);
                    lastPersistRef.set(now);
                }
            });

            task.setStatus("SUCCEEDED");
            task.setProgress(100);
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setErrorMessage(e.getMessage());
            task.setUpdatedAt(LocalDateTime.now());
            taskRepository.save(task);
        }
    }
}
