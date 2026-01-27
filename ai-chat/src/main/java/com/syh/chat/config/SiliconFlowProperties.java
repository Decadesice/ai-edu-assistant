package com.syh.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "siliconflow")
public class SiliconFlowProperties {
    private String baseUrl = "https://api.siliconflow.cn/v1";
    private String apiKey;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getApiKey() {
        if (apiKey != null && !apiKey.isBlank()) {
            return apiKey;
        }
        String fromUnderscore = System.getenv("SiliconFlow_Api_Key");
        if (fromUnderscore != null && !fromUnderscore.isBlank()) {
            return fromUnderscore;
        }
        String fromUpper = System.getenv("SILICONFLOW_API_KEY");
        if (fromUpper != null && !fromUpper.isBlank()) {
            return fromUpper;
        }
        String fromSpaced = System.getenv("SiliconFlow Api Key");
        if (fromSpaced != null && !fromSpaced.isBlank()) {
            return fromSpaced;
        }
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}

