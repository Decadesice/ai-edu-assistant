package com.syh.chat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "ingest_task")
public class IngestTask {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long documentId;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(nullable = false)
    private Integer progress;

    @Column(nullable = false)
    private Integer processedSegments;

    @Column(nullable = false)
    private Integer totalSegments;

    @Column(nullable = false, length = 1024)
    private String filePath;

    @Column(columnDefinition = "LONGTEXT")
    private String errorMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
