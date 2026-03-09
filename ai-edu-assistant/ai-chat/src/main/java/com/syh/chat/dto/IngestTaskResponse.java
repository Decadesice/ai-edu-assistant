package com.syh.chat.dto;

import java.time.LocalDateTime;

public class IngestTaskResponse {
    private String taskId;
    private Long documentId;
    private String status;
    private Integer progress;
    private Integer processedSegments;
    private Integer totalSegments;
    private String errorMessage;
    private Integer attemptCount;
    private LocalDateTime nextRetryAt;
    private String lastError;
    private LocalDateTime updatedAt;

    public IngestTaskResponse() {
    }

    public IngestTaskResponse(String taskId, Long documentId, String status, Integer progress, Integer processedSegments, Integer totalSegments, String errorMessage, Integer attemptCount, LocalDateTime nextRetryAt, String lastError, LocalDateTime updatedAt) {
        this.taskId = taskId;
        this.documentId = documentId;
        this.status = status;
        this.progress = progress;
        this.processedSegments = processedSegments;
        this.totalSegments = totalSegments;
        this.errorMessage = errorMessage;
        this.attemptCount = attemptCount;
        this.nextRetryAt = nextRetryAt;
        this.lastError = lastError;
        this.updatedAt = updatedAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public Integer getProcessedSegments() {
        return processedSegments;
    }

    public void setProcessedSegments(Integer processedSegments) {
        this.processedSegments = processedSegments;
    }

    public Integer getTotalSegments() {
        return totalSegments;
    }

    public void setTotalSegments(Integer totalSegments) {
        this.totalSegments = totalSegments;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(Integer attemptCount) {
        this.attemptCount = attemptCount;
    }

    public LocalDateTime getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(LocalDateTime nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
