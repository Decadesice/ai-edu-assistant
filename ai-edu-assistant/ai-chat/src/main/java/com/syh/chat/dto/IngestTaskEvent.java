package com.syh.chat.dto;

public class IngestTaskEvent {

    private String taskId;
    private Long userId;
    private Long documentId;
    private String filePath;

    public IngestTaskEvent() {
    }

    public IngestTaskEvent(String taskId, Long userId, Long documentId, String filePath) {
        this.taskId = taskId;
        this.userId = userId;
        this.documentId = documentId;
        this.filePath = filePath;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
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

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
}
