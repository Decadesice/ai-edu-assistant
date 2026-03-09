package com.syh.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syh.chat.config.BigModelProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BigModelService {

    private static final Logger log = LoggerFactory.getLogger(BigModelService.class);

    private static final List<String> MODEL_PRIORITY = List.of(
            "GLM-4.7-Flash",
            "GLM-4.6V-Flash",
            "GLM-4.1V-Thinking-Flash"
    );

    private final WebClient webClient;
    private final BigModelProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BigModelService(WebClient bigModelWebClient, BigModelProperties properties) {
        this.webClient = bigModelWebClient;
        this.properties = properties;
    }

    public String chatWithFallback(List<Map<String, String>> messages) {
        RuntimeException lastError = null;
        for (int i = 0; i < MODEL_PRIORITY.size(); i++) {
            String model = MODEL_PRIORITY.get(i);
            try {
                log.info("尝试使用模型: {} (优先级 {})", model, i + 1);
                String response = chatOnce(model, messages);
                if (response != null && !response.isBlank()) {
                    log.info("模型 {} 调用成功", model);
                    return response;
                }
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("模型 {} 调用失败: {}", model, e.getMessage());
                if (isRateLimitError(e) && i + 1 < MODEL_PRIORITY.size()) {
                    log.info("检测到限流，自动降级到下一级模型");
                    continue;
                }
                throw e;
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IllegalStateException("所有模型调用均失败");
    }

    public String chatOnce(String model, List<Map<String, String>> messages) {
        String apiKey = properties.getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("BigModel API Key 未配置，请设置环境变量 BIGMODEL_API_KEY");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        ArrayNode msgArray = body.putArray("messages");
        for (Map<String, String> msg : messages) {
            ObjectNode msgNode = msgArray.addObject();
            msgNode.put("role", msg.getOrDefault("role", "user"));
            msgNode.put("content", msg.getOrDefault("content", ""));
        }

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("构建请求失败", e);
        }

        try {
            String response = webClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofMillis(properties.getReadTimeoutMs()))
                    .block();

            return extractContent(response);
        } catch (WebClientResponseException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("BigModel API 错误: status={}, body={}", e.getStatusCode(), errorBody);
            if (isRateLimitError(e)) {
                throw new RuntimeException("模型限流: " + model, e);
            }
            throw new RuntimeException("BigModel API 调用失败: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new RuntimeException("BigModel 调用异常: " + e.getMessage(), e);
        }
    }

    private String extractContent(String response) {
        if (response == null || response.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode content = choices.get(0).path("message").path("content");
                if (!content.isMissingNode()) {
                    return content.asText("");
                }
            }
        } catch (Exception e) {
            log.warn("解析 BigModel 响应失败: {}", e.getMessage());
        }
        return "";
    }

    private boolean isRateLimitError(Exception e) {
        if (e instanceof WebClientResponseException w) {
            int code = w.getStatusCode().value();
            if (code == 429) {
                return true;
            }
            String body = w.getResponseBodyAsString();
            if (body != null && (body.contains("rate") || body.contains("limit") || body.contains("quota"))) {
                return true;
            }
        }
        String msg = e.getMessage();
        if (msg == null) return false;
        return msg.contains("429") || msg.toLowerCase().contains("rate limit") || msg.toLowerCase().contains("quota");
    }

    public List<String> getModelPriority() {
        return new ArrayList<>(MODEL_PRIORITY);
    }
}
