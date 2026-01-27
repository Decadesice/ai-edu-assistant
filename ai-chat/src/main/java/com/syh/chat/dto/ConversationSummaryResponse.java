package com.syh.chat.dto;

import java.time.LocalDateTime;

public class ConversationSummaryResponse {
    private String sessionId;
    private String title;
    private String modelName;
    private LocalDateTime updatedAt;

    public ConversationSummaryResponse() {
    }

    public ConversationSummaryResponse(String sessionId, String title, String modelName, LocalDateTime updatedAt) {
        this.sessionId = sessionId;
        this.title = title;
        this.modelName = modelName;
        this.updatedAt = updatedAt;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}


