package com.syh.chat.dto;

import java.util.List;

public class RagContextResponse {
    private List<RagSnippet> snippets;

    public RagContextResponse() {
    }

    public RagContextResponse(List<RagSnippet> snippets) {
        this.snippets = snippets;
    }

    public List<RagSnippet> getSnippets() {
        return snippets;
    }

    public void setSnippets(List<RagSnippet> snippets) {
        this.snippets = snippets;
    }

    public static class RagSnippet {
        private Long documentId;
        private Integer segmentIndex;
        private String content;

        public RagSnippet() {
        }

        public RagSnippet(Long documentId, Integer segmentIndex, String content) {
            this.documentId = documentId;
            this.segmentIndex = segmentIndex;
            this.content = content;
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
    }
}


