package com.syh.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ratelimit")
public class RateLimitProperties {

    private Limit model = new Limit(3, 1);
    private Limit upload = new Limit(2, 60);

    public Limit getModel() {
        return model;
    }

    public void setModel(Limit model) {
        this.model = model;
    }

    public Limit getUpload() {
        return upload;
    }

    public void setUpload(Limit upload) {
        this.upload = upload;
    }

    public static class Limit {
        private int limit;
        private int windowSeconds;

        public Limit() {
        }

        public Limit(int limit, int windowSeconds) {
            this.limit = limit;
            this.windowSeconds = windowSeconds;
        }

        public int getLimit() {
            return limit;
        }

        public void setLimit(int limit) {
            this.limit = limit;
        }

        public int getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(int windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }
}
