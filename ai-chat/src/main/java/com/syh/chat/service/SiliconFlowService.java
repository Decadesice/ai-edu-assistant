package com.syh.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syh.chat.config.SiliconFlowProperties;
import com.syh.chat.model.Message;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.charset.StandardCharsets;

@Service
public class SiliconFlowService {

    private final WebClient streamWebClient;
    private final WebClient onceWebClient;
    private final SiliconFlowProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SiliconFlowService(
            @Qualifier("siliconFlowWebClient") WebClient streamWebClient,
            @Qualifier("siliconFlowLongWebClient") WebClient onceWebClient,
            SiliconFlowProperties properties
    ) {
        this.streamWebClient = streamWebClient;
        this.onceWebClient = onceWebClient;
        this.properties = properties;
    }

    @CircuitBreaker(name = "siliconflow", fallbackMethod = "chatOnceFallback")
    @Retry(name = "siliconflow")
    public Mono<String> chatOnce(List<Message> messages, String modelName) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return Mono.error(new IllegalStateException("SiliconFlow API Key 未配置，请设置环境变量 SiliconFlow_Api_Key / SILICONFLOW_API_KEY 或 siliconflow.api-key"));
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", normalizeModelName(modelName));
        body.put("stream", false);
        body.set("messages", buildMessages(messages));

        return onceWebClient.post()
                .uri("/chat/completions")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), resp -> resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new IllegalStateException("SiliconFlow API 调用失败: HTTP " + resp.statusCode().value() + " " + b)))
                .bodyToMono(String.class)
                .map(this::parseChatOnceContent);
    }

    @CircuitBreaker(name = "siliconflow", fallbackMethod = "chatStreamFallback")
    @Retry(name = "siliconflow")
    public Flux<BigModelService.BigModelDelta> chatStream(List<Message> messages, String modelName) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return Flux.error(new IllegalStateException("SiliconFlow API Key 未配置，请设置环境变量 SiliconFlow_Api_Key / SILICONFLOW_API_KEY 或 siliconflow.api-key"));
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", normalizeModelName(modelName));
        body.put("stream", true);
        body.set("messages", buildMessages(messages));

        AtomicBoolean doneEmitted = new AtomicBoolean(false);

        return streamWebClient.post()
                .uri("/chat/completions")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .bodyValue(body)
                .exchangeToFlux(resp -> {
                    if (resp.statusCode().isError()) {
                        return resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMapMany(b -> Flux.error(new IllegalStateException("SiliconFlow API 调用失败: HTTP " + resp.statusCode().value() + " " + b)));
                    }
                    return resp.bodyToFlux(DataBuffer.class);
                })
                .transform(this::splitToLines)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(line -> line.startsWith("data:") ? line.substring(5).trim() : line)
                .filter(line -> !line.isEmpty())
                .flatMap(line -> {
                    if ("[DONE]".equals(line)) {
                        doneEmitted.set(true);
                        return Flux.just(BigModelService.BigModelDelta.done());
                    }
                    try {
                        JsonNode root = objectMapper.readTree(line);
                        BigModelService.BigModelDelta delta = parseStreamDelta(root);
                        return delta == null ? Flux.empty() : Flux.just(delta);
                    } catch (Exception e) {
                        return Flux.empty();
                    }
                })
                .concatWith(Mono.defer(() -> doneEmitted.get() ? Mono.empty() : Mono.just(BigModelService.BigModelDelta.done())));
    }

    @SuppressWarnings("unused")
    private Mono<String> chatOnceFallback(List<Message> messages, String modelName, Throwable cause) {
        if (cause != null) {
            return Mono.error(cause);
        }
        return Mono.error(new IllegalStateException("SiliconFlow 服务繁忙或不可用，请稍后重试"));
    }

    @SuppressWarnings("unused")
    private Flux<BigModelService.BigModelDelta> chatStreamFallback(List<Message> messages, String modelName, Throwable cause) {
        if (cause != null) {
            return Flux.error(cause);
        }
        return Flux.error(new IllegalStateException("SiliconFlow 服务繁忙或不可用，请稍后重试"));
    }

    private Flux<String> splitToLines(Flux<DataBuffer> chunks) {
        return Flux.defer(() -> {
            AtomicReference<byte[]> pendingRef = new AtomicReference<>(new byte[0]);
            return chunks.concatMap(buf -> {
                        if (buf == null) {
                            return Flux.empty();
                        }
                        try {
                            int n = buf.readableByteCount();
                            if (n <= 0) {
                                return Flux.empty();
                            }
                            byte[] bytes = new byte[n];
                            buf.read(bytes);

                            byte[] pending = pendingRef.get();
                            byte[] merged = new byte[pending.length + bytes.length];
                            System.arraycopy(pending, 0, merged, 0, pending.length);
                            System.arraycopy(bytes, 0, merged, pending.length, bytes.length);

                            List<String> out = new ArrayList<>();
                            int start = 0;
                            for (int i = 0; i < merged.length; i++) {
                                if (merged[i] == (byte) '\n') {
                                    int end = i;
                                    if (end > start && merged[end - 1] == (byte) '\r') {
                                        end -= 1;
                                    }
                                    String line = new String(merged, start, end - start, StandardCharsets.UTF_8);
                                    out.add(line);
                                    start = i + 1;
                                }
                            }

                            if (start >= merged.length) {
                                pendingRef.set(new byte[0]);
                            } else {
                                byte[] rest = new byte[merged.length - start];
                                System.arraycopy(merged, start, rest, 0, rest.length);
                                pendingRef.set(rest);
                            }
                            return Flux.fromIterable(out);
                        } finally {
                            DataBufferUtils.release(buf);
                        }
                    })
                    .concatWith(Mono.defer(() -> {
                        byte[] rest = pendingRef.get();
                        if (rest == null || rest.length == 0) {
                            return Mono.empty();
                        }
                        pendingRef.set(new byte[0]);
                        return Mono.just(new String(rest, StandardCharsets.UTF_8));
                    }));
        });
    }

    private String parseChatOnceContent(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && choices.size() > 0) {
                JsonNode first = choices.get(0);
                JsonNode message = first.path("message");
                if (!message.isMissingNode()) {
                    return message.path("content").asText("");
                }
                JsonNode delta = first.path("delta");
                if (!delta.isMissingNode()) {
                    return delta.path("content").asText("");
                }
            }
            JsonNode message = root.path("message");
            if (!message.isMissingNode()) {
                return message.path("content").asText("");
            }
            return root.path("content").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private String normalizeModelName(String modelName) {
        String m = modelName == null ? "" : modelName.trim();
        if (m.isEmpty()) {
            return m;
        }
        if ("GLM-4.1V-9B-Thinking".equalsIgnoreCase(m) || "glm-4.1v-9b-thinking".equalsIgnoreCase(m)) {
            return "THUDM/GLM-4.1V-9B-Thinking";
        }
        return m;
    }

    private BigModelService.BigModelDelta parseStreamDelta(JsonNode root) {
        JsonNode choices = root.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            JsonNode first = choices.get(0);
            JsonNode delta = first.path("delta");
            if (!delta.isMissingNode()) {
                String content = delta.path("content").asText("");
                String reasoning = delta.path("reasoning_content").asText("");
                if (reasoning.isBlank()) {
                    reasoning = delta.path("reasoning").asText("");
                }
                return new BigModelService.BigModelDelta(content, reasoning, false);
            }
            JsonNode message = first.path("message");
            if (!message.isMissingNode()) {
                String content = message.path("content").asText("");
                String reasoning = message.path("reasoning_content").asText("");
                if (reasoning.isBlank()) {
                    reasoning = message.path("reasoning").asText("");
                }
                return new BigModelService.BigModelDelta(content, reasoning, false);
            }
        }
        return null;
    }

    private ArrayNode buildMessages(List<Message> messages) {
        ArrayNode out = objectMapper.createArrayNode();
        for (Message m : messages) {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("role", m.getRole());

            boolean hasImages = m.getImages() != null && !m.getImages().isEmpty();
            if ("user".equalsIgnoreCase(m.getRole()) && hasImages) {
                ArrayNode parts = objectMapper.createArrayNode();
                if (m.getContent() != null && !m.getContent().isBlank()) {
                    ObjectNode p = objectMapper.createObjectNode();
                    p.put("type", "text");
                    p.put("text", m.getContent());
                    parts.add(p);
                }
                for (String img : m.getImages()) {
                    if (img == null || img.isBlank()) continue;
                    ObjectNode p = objectMapper.createObjectNode();
                    p.put("type", "image_url");
                    ObjectNode imageUrl = objectMapper.createObjectNode();
                    imageUrl.put("detail", "auto");
                    imageUrl.put("url", img);
                    p.set("image_url", imageUrl);
                    parts.add(p);
                }
                msg.set("content", parts);
            } else {
                msg.put("content", m.getContent() == null ? "" : m.getContent());
            }

            out.add(msg);
        }
        return out;
    }
}

