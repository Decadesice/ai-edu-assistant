package com.syh.chat.dto;

import java.util.List;

public class QuestionGenerateRequest {
    private Long documentId;
    private String chapterHint;
    private Integer count;
    private String model;
    private List<String> types;

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public String getChapterHint() {
        return chapterHint;
    }

    public void setChapterHint(String chapterHint) {
        this.chapterHint = chapterHint;
    }

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<String> getTypes() {
        return types;
    }

    public void setTypes(List<String> types) {
        this.types = types;
    }
}

