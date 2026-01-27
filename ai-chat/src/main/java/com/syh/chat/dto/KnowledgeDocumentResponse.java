package com.syh.chat.dto;

import java.time.LocalDateTime;

public class KnowledgeDocumentResponse {
    private Long id;
    private String title;
    private String status;
    private Integer segmentCount;
    private LocalDateTime updatedAt;

    public KnowledgeDocumentResponse() {
    }

    public KnowledgeDocumentResponse(Long id, String title, String status, Integer segmentCount, LocalDateTime updatedAt) {
        this.id = id;
        this.title = title;
        this.status = status;
        this.segmentCount = segmentCount;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getSegmentCount() {
        return segmentCount;
    }

    public void setSegmentCount(Integer segmentCount) {
        this.segmentCount = segmentCount;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}


