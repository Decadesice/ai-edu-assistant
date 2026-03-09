package com.syh.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ollama")
public class OllamaProperties {
    private String baseUrl = "http://localhost:11434";
    private String embeddingModel = "bge-m3";
    private String chatModelSmall = "qwen3.5:0.8b";
    private String chatModelMedium = "qwen3.5:2b";
    private String chatModelLarge = "qwen3.5:4b";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getChatModelSmall() {
        return chatModelSmall;
    }

    public void setChatModelSmall(String chatModelSmall) {
        this.chatModelSmall = chatModelSmall;
    }

    public String getChatModelMedium() {
        return chatModelMedium;
    }

    public void setChatModelMedium(String chatModelMedium) {
        this.chatModelMedium = chatModelMedium;
    }

    public String getChatModelLarge() {
        return chatModelLarge;
    }

    public void setChatModelLarge(String chatModelLarge) {
        this.chatModelLarge = chatModelLarge;
    }
}
