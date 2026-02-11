package com.syh.chat.service;

import com.syh.chat.entity.IngestTask;
import com.syh.chat.entity.KnowledgeDocument;
import com.syh.chat.repository.IngestTaskRepository;
import com.syh.chat.repository.KnowledgeDocumentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class IngestTaskProcessor {

    private final IngestTaskRepository taskRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeIngestService knowledgeIngestService;
    private final IngestTaskStateService stateService;
    private final MeterRegistry meterRegistry;

    public IngestTaskProcessor(
            IngestTaskRepository taskRepository,
            KnowledgeDocumentRepository documentRepository,
            KnowledgeIngestService knowledgeIngestService,
            IngestTaskStateService stateService,
            MeterRegistry meterRegistry
    ) {
        this.taskRepository = taskRepository;
        this.documentRepository = documentRepository;
        this.knowledgeIngestService = knowledgeIngestService;
        this.stateService = stateService;
        this.meterRegistry = meterRegistry;
    }

    public IngestTaskProcessingResult process(String taskId, Long userId, Long documentId, String filePath) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "skipped";
        if (taskId == null || userId == null || documentId == null || filePath == null) {
            Counter.builder("ingest_task_process_total").tag("result", "skipped").register(meterRegistry).increment();
            sample.stop(Timer.builder("ingest_task_process_seconds").tag("result", "skipped").register(meterRegistry));
            return IngestTaskProcessingResult.SKIPPED;
        }
        Optional<IngestTask> taskOpt = taskRepository.findById(Objects.requireNonNull(taskId));
        if (taskOpt.isEmpty()) {
            Counter.builder("ingest_task_process_total").tag("result", "skipped").register(meterRegistry).increment();
            sample.stop(Timer.builder("ingest_task_process_seconds").tag("result", "skipped").register(meterRegistry));
            return IngestTaskProcessingResult.SKIPPED;
        }
        IngestTask task = taskOpt.get();
        if (IngestTaskStateService.STATUS_SUCCEEDED.equalsIgnoreCase(task.getStatus())
                || IngestTaskStateService.STATUS_DEAD.equalsIgnoreCase(task.getStatus())) {
            Counter.builder("ingest_task_process_total").tag("result", "skipped").register(meterRegistry).increment();
            sample.stop(Timer.builder("ingest_task_process_seconds").tag("result", "skipped").register(meterRegistry));
            return IngestTaskProcessingResult.SKIPPED;
        }

        LocalDateTime now = LocalDateTime.now();
        if (IngestTaskStateService.STATUS_RETRYING.equalsIgnoreCase(task.getStatus())
                && task.getNextRetryAt() != null
                && task.getNextRetryAt().isAfter(now)) {
            Counter.builder("ingest_task_process_total").tag("result", "not_due").register(meterRegistry).increment();
            sample.stop(Timer.builder("ingest_task_process_seconds").tag("result", "not_due").register(meterRegistry));
            return IngestTaskProcessingResult.NOT_DUE;
        }

        if (!stateService.tryMarkRunning(task, now)) {
            Counter.builder("ingest_task_process_total").tag("result", "busy").register(meterRegistry).increment();
            sample.stop(Timer.builder("ingest_task_process_seconds").tag("result", "busy").register(meterRegistry));
            return IngestTaskProcessingResult.BUSY;
        }
        task.setStatus(IngestTaskStateService.STATUS_RUNNING);
        task.setUpdatedAt(now);

        Optional<KnowledgeDocument> docOpt = documentRepository.findById(Objects.requireNonNull(documentId));
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
                Instant now1 = Instant.now();
                Instant lastPersist = lastPersistRef.get();
                if (processed == total || processed % 5 == 0 || Duration.between(lastPersist, now1).toMillis() > 1000) {
                    task.setUpdatedAt(LocalDateTime.now());
                    taskRepository.save(task);
                    lastPersistRef.set(now1);
                }
            });

            stateService.markSucceeded(task, LocalDateTime.now());
            outcome = "succeeded";
            Counter.builder("ingest_task_process_total").tag("result", outcome).register(meterRegistry).increment();
            sample.stop(Timer.builder("ingest_task_process_seconds").tag("result", outcome).register(meterRegistry));
            return IngestTaskProcessingResult.SUCCEEDED;
        } catch (Exception e) {
            IngestTaskProcessingResult result = stateService.markFailure(task, e, LocalDateTime.now());
            outcome = (result == IngestTaskProcessingResult.DEAD) ? "dead" : "retry";
            Counter.builder("ingest_task_process_total").tag("result", outcome).register(meterRegistry).increment();
            sample.stop(Timer.builder("ingest_task_process_seconds").tag("result", outcome).register(meterRegistry));
            return result;
        }
    }
}
