package com.syh.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syh.chat.config.OllamaProperties;
import com.syh.chat.model.Message;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
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
public class OllamaService {

    private final WebClient streamWebClient;
    private final WebClient onceWebClient;
    private final OllamaProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaService(
            @Qualifier("ollamaWebClient") WebClient streamWebClient,
            @Qualifier("ollamaLongWebClient") WebClient onceWebClient,
            OllamaProperties properties
    ) {
        this.streamWebClient = streamWebClient;
        this.onceWebClient = onceWebClient;
        this.properties = properties;
    }

    @CircuitBreaker(name = "ollama", fallbackMethod = "chatOnceFallback")
    @Retry(name = "ollama")
    public Mono<String> chatOnce(List<Message> messages, String modelName) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", normalizeModelName(modelName));
        body.put("stream", false);
        body.set("messages", buildMessages(messages));
        ObjectNode options = objectMapper.createObjectNode();
        options.put("thinking", true);
        body.set("options", options);

        return onceWebClient.post()
                .uri("/api/chat")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), resp -> resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new IllegalStateException("Ollama API 调用失败: HTTP " + resp.statusCode().value() + " " + b)))
                .bodyToMono(String.class)
                .map(this::parseChatOnceContent);
    }

    @CircuitBreaker(name = "ollama", fallbackMethod = "chatStreamFallback")
    @Retry(name = "ollama")
    public Flux<ChatDelta> chatStream(List<Message> messages, String modelName) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", normalizeModelName(modelName));
        body.put("stream", true);
        body.set("messages", buildMessages(messages));
        ObjectNode options = objectMapper.createObjectNode();
        options.put("thinking", true);
        body.set("options", options);

        AtomicBoolean doneEmitted = new AtomicBoolean(false);

        return streamWebClient.post()
                .uri("/api/chat")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .bodyValue(body)
                .exchangeToFlux(resp -> {
                    if (resp.statusCode().isError()) {
                        return resp.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMapMany(b -> Flux.error(new IllegalStateException("Ollama API 调用失败: HTTP " + resp.statusCode().value() + " " + b)));
                    }
                    return resp.bodyToFlux(DataBuffer.class);
                })
                .transform(this::splitToLines)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .flatMap(line -> {
                    try {
                        JsonNode root = objectMapper.readTree(line);
                        if (root.path("done").asBoolean(false)) {
                            doneEmitted.set(true);
                            return Flux.just(ChatDelta.done());
                        }
                        ChatDelta delta = parseStreamDelta(root);
                        return delta == null ? Flux.empty() : Flux.just(delta);
                    } catch (Exception e) {
                        return Flux.empty();
                    }
                })
                .concatWith(Mono.defer(() -> doneEmitted.get() ? Mono.empty() : Mono.just(ChatDelta.done())));
    }

    @SuppressWarnings("unused")
    private Mono<String> chatOnceFallback(List<Message> messages, String modelName, Throwable cause) {
        if (cause != null) {
            return Mono.error(cause);
        }
        return Mono.error(new IllegalStateException("Ollama 服务繁忙或不可用，请稍后重试"));
    }

    @SuppressWarnings("unused")
    private Flux<ChatDelta> chatStreamFallback(List<Message> messages, String modelName, Throwable cause) {
        if (cause != null) {
            return Flux.error(cause);
        }
        return Flux.error(new IllegalStateException("Ollama 服务繁忙或不可用，请稍后重试"));
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
            return properties.getChatModelSmall();
        }
        if ("qwen3.5:0.8b".equalsIgnoreCase(m) || "small".equalsIgnoreCase(m)) {
            return properties.getChatModelSmall();
        }
        if ("qwen3.5:2b".equalsIgnoreCase(m) || "medium".equalsIgnoreCase(m)) {
            return properties.getChatModelMedium();
        }
        if ("qwen3.5:4b".equalsIgnoreCase(m) || "large".equalsIgnoreCase(m)) {
            return properties.getChatModelLarge();
        }
        return m;
    }

    private ChatDelta parseStreamDelta(JsonNode root) {
        JsonNode message = root.path("message");
        if (!message.isMissingNode()) {
            String content = message.path("content").asText("");
            String reasoning = "";
            JsonNode thinkingNode = root.path("thinking");
            if (!thinkingNode.isMissingNode()) {
                reasoning = thinkingNode.asText("");
            }
            if (reasoning.isBlank()) {
                reasoning = message.path("thinking").asText("");
            }
            if (reasoning.isBlank()) {
                reasoning = message.path("reasoning_content").asText("");
            }
            return new ChatDelta(content, reasoning, false);
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
