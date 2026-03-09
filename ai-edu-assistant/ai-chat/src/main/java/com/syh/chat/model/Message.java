package com.syh.chat.model;

import java.util.List;

public class Message {
    private String role;
    private String content;
    private List<String> images;

    public Message() {
    }

    public Message(String role, String content) {
        this.role = role;
        this.content = content;
    }

    public Message(String role, String content, List<String> images) {
        this.role = role;
        this.content = content;
        this.images = images;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public List<String> getImages() {
        return images;
    }

    public void setImages(List<String> images) {
        this.images = images;
    }
}
