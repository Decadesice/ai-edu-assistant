package com.syh.chat.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_segment")
public class KnowledgeSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private Integer segmentIndex;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false, length = 80)
    private String chromaId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

    public Integer getSegmentIndex() {
        return segmentIndex;
    }

    public void setSegmentIndex(Integer segmentIndex) {
        this.segmentIndex = segmentIndex;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getChromaId() {
        return chromaId;
    }

    public void setChromaId(String chromaId) {
        this.chromaId = chromaId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}


