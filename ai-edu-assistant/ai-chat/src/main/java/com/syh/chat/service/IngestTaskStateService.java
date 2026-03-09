package com.syh.chat.service;

import com.syh.chat.entity.IngestTask;
import com.syh.chat.entity.IngestTaskTransition;
import com.syh.chat.repository.IngestTaskRepository;
import com.syh.chat.repository.IngestTaskTransitionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class IngestTaskStateService {

    public static final String STATUS_QUEUED = "QUEUED";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_RETRYING = "RETRYING";
    public static final String STATUS_SUCCEEDED = "SUCCEEDED";
    public static final String STATUS_DEAD = "DEAD";

    private final IngestTaskRepository taskRepository;
    private final IngestTaskTransitionRepository transitionRepository;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Duration maxBackoff;

    public IngestTaskStateService(
            IngestTaskRepository taskRepository,
            IngestTaskTransitionRepository transitionRepository,
            @Value("${app.ingest.redis.max-attempts:10}") int maxAttempts,
            @Value("${app.ingest.redis.retry.base-backoff-ms:1000}") long baseBackoffMs,
            @Value("${app.ingest.redis.retry.max-backoff-ms:600000}") long maxBackoffMs
    ) {
        this.taskRepository = taskRepository;
        this.transitionRepository = transitionRepository;
        this.maxAttempts = maxAttempts;
        this.baseBackoff = Duration.ofMillis(Math.max(0, baseBackoffMs));
        this.maxBackoff = Duration.ofMillis(Math.max(0, maxBackoffMs));
    }

    @Transactional
    public boolean tryMarkRunning(IngestTask task, LocalDateTime now) {
        String from = task.getStatus();
        int updated = taskRepository.transitionIfEligible(
                task.getId(),
                List.of(STATUS_QUEUED, STATUS_RETRYING),
                STATUS_RUNNING,
                now
        );
        if (updated <= 0) {
            return false;
        }
        recordTransition(task.getId(), from, STATUS_RUNNING, task.getAttemptCount(), null, now);
        return true;
    }

    @Transactional
    public void markSucceeded(IngestTask task, LocalDateTime now) {
        String from = task.getStatus();
        task.setStatus(STATUS_SUCCEEDED);
        task.setProgress(100);
        task.setNextRetryAt(null);
        task.setLastError(null);
        task.setUpdatedAt(now);
        taskRepository.save(task);
        recordTransition(task.getId(), from, STATUS_SUCCEEDED, task.getAttemptCount(), null, now);
    }

    @Transactional
    public IngestTaskProcessingResult markFailure(IngestTask task, Exception error, LocalDateTime now) {
        String from = task.getStatus();
        int currentAttempt = task.getAttemptCount() == null ? 0 : task.getAttemptCount();
        int nextAttempt = currentAttempt + 1;

        task.setAttemptCount(nextAttempt);
        String msg = errorMessage(error);
        task.setErrorMessage(msg);
        task.setLastError(msg);

        if (nextAttempt >= maxAttempts) {
            task.setStatus(STATUS_DEAD);
            task.setNextRetryAt(null);
            task.setUpdatedAt(now);
            taskRepository.save(task);
            recordTransition(task.getId(), from, STATUS_DEAD, nextAttempt, msg, now);
            return IngestTaskProcessingResult.DEAD;
        }

        task.setStatus(STATUS_RETRYING);
        task.setNextRetryAt(now.plus(computeBackoff(nextAttempt)));
        task.setUpdatedAt(now);
        taskRepository.save(task);
        recordTransition(task.getId(), from, STATUS_RETRYING, nextAttempt, msg, now);
        return IngestTaskProcessingResult.RETRY;
    }

    private Duration computeBackoff(int attempt) {
        if (attempt <= 0) {
            return Duration.ZERO;
        }
        long multiplier;
        if (attempt >= 31) {
            multiplier = Long.MAX_VALUE;
        } else {
            multiplier = 1L << (attempt - 1);
        }
        long millis;
        try {
            millis = Math.multiplyExact(baseBackoff.toMillis(), multiplier);
        } catch (ArithmeticException e) {
            millis = Long.MAX_VALUE;
        }
        millis = Math.min(millis, maxBackoff.toMillis());
        if (millis < 0) {
            millis = maxBackoff.toMillis();
        }
        return Duration.ofMillis(millis);
    }

    private void recordTransition(String taskId, String from, String to, Integer attemptCount, String message, LocalDateTime now) {
        IngestTaskTransition t = new IngestTaskTransition();
        t.setTaskId(taskId);
        t.setFromStatus(from);
        t.setToStatus(to);
        t.setAttemptCount(attemptCount);
        t.setMessage(truncate(message, 2000));
        t.setCreatedAt(now);
        transitionRepository.save(t);
    }

    private String errorMessage(Exception e) {
        if (e == null) {
            return null;
        }
        String base = e.getMessage() == null ? "" : e.getMessage();
        String name = e.getClass().getSimpleName();
        String combined = name.isBlank() ? base : (name + ": " + base);
        return truncate(combined, 2000);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        if (s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, maxLen);
    }
}

