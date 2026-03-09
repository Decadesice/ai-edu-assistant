package com.syh.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "bigmodel")
public class BigModelProperties {

    private String apiKey;
    private String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
    private int connectTimeoutMs = 5000;
    private int readTimeoutMs = 120000;

    public String getApiKey() {
        String key = apiKey;
        if (key == null || key.isBlank()) {
            key = System.getenv("BIGMODEL_API_KEY");
        }
        return key;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }
}
