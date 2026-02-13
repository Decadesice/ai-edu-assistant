package com.syh.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syh.chat.config.BigModelProperties;
import com.syh.chat.model.Message;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.nio.charset.StandardCharsets;

@Service
public class BigModelService {

    private final WebClient streamWebClient;
    private final WebClient onceWebClient;
    private final BigModelProperties properties;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BigModelService(
            @Qualifier("bigModelWebClient") WebClient streamWebClient,
            @Qualifier("bigModelLongWebClient") WebClient onceWebClient,
            BigModelProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.streamWebClient = streamWebClient;
        this.onceWebClient = onceWebClient;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @CircuitBreaker(name = "bigmodel", fallbackMethod = "chatOnceFallback")
    @Retry(name = "bigmodel")
    public Mono<BigModelReply> chatOnce(List<Message> messages, String modelName) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return Mono.error(new IllegalStateException("BigModel API Key 未配置，请设置环境变量 BIGMODEL_API_KEY 或 bigmodel.api-key"));
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("stream", false);
        body.set("messages", buildMessages(messages));

        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            Timer timer = Timer.builder("ai_model_request_seconds")
                    .tag("provider", "bigmodel")
                    .tag("model", modelName == null ? "" : modelName)
                    .tag("stream", "false")
                    .register(meterRegistry);
            Counter failures = Counter.builder("ai_model_request_failures_total")
                    .tag("provider", "bigmodel")
                    .tag("model", modelName == null ? "" : modelName)
                    .tag("stream", "false")
                    .register(meterRegistry);

            return onceWebClient.post()
                    .uri("/api/paas/v4/chat/completions")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .bodyValue(body)
                    .exchangeToMono(resp -> {
                        if (resp.statusCode().isError()) {
                            return resp.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(b -> Mono.error(new IllegalStateException("BigModel API 调用失败: HTTP " + resp.statusCode().value() + " " + trimErrorBody(b))));
                        }
                        return resp.bodyToMono(String.class);
                    })
                    .map(this::parseReply)
                    .doOnError(e -> failures.increment())
                    .doFinally(sig -> sample.stop(timer));
        });
    }

    @CircuitBreaker(name = "bigmodel", fallbackMethod = "chatStreamFallback")
    @Retry(name = "bigmodel")
    public Flux<BigModelDelta> chatStream(List<Message> messages, String modelName) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            return Flux.error(new IllegalStateException("BigModel API Key 未配置，请设置环境变量 BIGMODEL_API_KEY 或 bigmodel.api-key"));
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName);
        body.put("stream", true);
        body.set("messages", buildMessages(messages));

        AtomicBoolean doneEmitted = new AtomicBoolean(false);

        return Flux.defer(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            Timer timer = Timer.builder("ai_model_request_seconds")
                    .tag("provider", "bigmodel")
                    .tag("model", modelName == null ? "" : modelName)
                    .tag("stream", "true")
                    .register(meterRegistry);
            Counter failures = Counter.builder("ai_model_request_failures_total")
                    .tag("provider", "bigmodel")
                    .tag("model", modelName == null ? "" : modelName)
                    .tag("stream", "true")
                    .register(meterRegistry);

            return streamWebClient.post()
                    .uri("/api/paas/v4/chat/completions")
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                    .bodyValue(body)
                    .exchangeToFlux(resp -> {
                        if (resp.statusCode().isError()) {
                            return resp.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMapMany(b -> Flux.error(new IllegalStateException("BigModel API 调用失败: HTTP " + resp.statusCode().value() + " " + trimErrorBody(b))));
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
                            return Flux.just(BigModelDelta.done());
                        }
                        try {
                            JsonNode root = objectMapper.readTree(line);
                            BigModelDelta delta = parseStreamDelta(root);
                            if (delta == null) {
                                return Flux.empty();
                            }
                            return Flux.just(delta);
                        } catch (Exception e) {
                            return Flux.empty();
                        }
                    })
                    .concatWith(Mono.defer(() -> doneEmitted.get() ? Mono.empty() : Mono.just(BigModelDelta.done())))
                    .doOnError(e -> failures.increment())
                    .doFinally(sig -> sample.stop(timer));
        });
    }

    private String trimErrorBody(String raw) {
        if (raw == null) return "";
        String s = raw.replaceAll("\\s+", " ").trim();
        if (s.length() > 400) {
            s = s.substring(0, 400) + "...";
        }
        return s;
    }

    @SuppressWarnings("unused")
    private Mono<BigModelReply> chatOnceFallback(List<Message> messages, String modelName, Throwable cause) {
        if (cause != null) {
            return Mono.error(cause);
        }
        return Mono.error(new IllegalStateException("大模型服务繁忙或不可用，请稍后重试"));
    }

    @SuppressWarnings("unused")
    private Flux<BigModelDelta> chatStreamFallback(List<Message> messages, String modelName, Throwable cause) {
        if (cause != null) {
            return Flux.error(cause);
        }
        return Flux.error(new IllegalStateException("大模型服务繁忙或不可用，请稍后重试"));
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

    public Mono<String> generateTitleOnce(String userText, String assistantText, boolean hasImage, int maxChars) {
        String cleanUser = userText == null ? "" : userText.replaceAll("\\s+", " ").trim();
        String cleanAssistant = assistantText == null ? "" : assistantText.replaceAll("\\s+", " ").trim();

        String prompt = "你是一个标题生成器。请根据以下首轮对话生成一个简短中文标题，严格不超过" + maxChars + "个汉字。"
                + "只输出标题本身，不要解释，不要引号，不要换行，不要以标点结尾。\n"
                + (hasImage ? "补充：用户发送了一张图片。\n" : "")
                + "用户：" + cleanUser + "\n"
                + "助手：" + cleanAssistant + "\n";

        Message m = new Message("user", prompt);
        return chatOnce(List.of(m), "glm-4.6v-Flash")
                .map(BigModelReply::getContent)
                .map(this::sanitizeTitle)
                .map(title -> {
                    if (title == null) {
                        return "";
                    }
                    String t = title.trim();
                    if (t.length() > maxChars) {
                        t = t.substring(0, maxChars);
                    }
                    return t;
                });
    }

    private BigModelDelta parseStreamDelta(JsonNode root) {
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
                return new BigModelDelta(content, reasoning, false);
            }
            JsonNode message = first.path("message");
            if (!message.isMissingNode()) {
                String content = message.path("content").asText("");
                String reasoning = message.path("reasoning_content").asText("");
                if (reasoning.isBlank()) {
                    reasoning = message.path("reasoning").asText("");
                }
                return new BigModelDelta(content, reasoning, false);
            }
        }
        JsonNode message = root.path("message");
        if (!message.isMissingNode()) {
            String content = message.path("content").asText("");
            String reasoning = message.path("reasoning_content").asText("");
            if (reasoning.isBlank()) {
                reasoning = message.path("reasoning").asText("");
            }
            return new BigModelDelta(content, reasoning, false);
        }
        return null;
    }

    private String sanitizeTitle(String raw) {
        if (raw == null) {
            return "";
        }
        String title = raw.trim();
        int newlineIdx = title.indexOf('\n');
        if (newlineIdx >= 0) {
            title = title.substring(0, newlineIdx).trim();
        }
        if ((title.startsWith("\"") && title.endsWith("\"")) || (title.startsWith("“") && title.endsWith("”"))) {
            title = title.substring(1, title.length() - 1).trim();
        }
        while (!title.isEmpty()) {
            char last = title.charAt(title.length() - 1);
            if (last == '。' || last == '！' || last == '？' || last == '.' || last == '!' || last == '?' || last == '：' || last == ':' || last == '，' || last == ',' || last == '；' || last == ';') {
                title = title.substring(0, title.length() - 1).trim();
            } else {
                break;
            }
        }
        return title;
    }

    private ArrayNode buildMessages(List<Message> messages) {
        ArrayNode out = objectMapper.createArrayNode();
        for (Message m : messages) {
            ObjectNode msg = objectMapper.createObjectNode();
            msg.put("role", m.getRole());

            boolean hasImages = m.getImages() != null && !m.getImages().isEmpty();
            if ("user".equalsIgnoreCase(m.getRole()) && (hasImages)) {
                ArrayNode parts = objectMapper.createArrayNode();
                for (String img : m.getImages()) {
                    if (img == null || img.isBlank()) continue;
                    ObjectNode p = objectMapper.createObjectNode();
                    p.put("type", "image_url");
                    ObjectNode imageUrl = objectMapper.createObjectNode();
                    imageUrl.put("url", img);
                    p.set("image_url", imageUrl);
                    parts.add(p);
                }
                if (m.getContent() != null && !m.getContent().isBlank()) {
                    ObjectNode p = objectMapper.createObjectNode();
                    p.put("type", "text");
                    p.put("text", m.getContent());
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

    private BigModelReply parseReply(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode choices = root.path("choices");
            JsonNode first = choices.isArray() && choices.size() > 0 ? choices.get(0) : null;
            JsonNode message = first == null ? null : first.path("message");
            String content = message == null ? "" : message.path("content").asText("");
            String reasoning = message == null ? "" : message.path("reasoning_content").asText("");
            return new BigModelReply(content, reasoning);
        } catch (Exception e) {
            return new BigModelReply("", "");
        }
    }

    public static class BigModelReply {
        private final String content;
        private final String reasoningContent;

        public BigModelReply(String content, String reasoningContent) {
            this.content = content;
            this.reasoningContent = reasoningContent;
        }

        public String getContent() {
            return content;
        }

        public String getReasoningContent() {
            return reasoningContent;
        }
    }

    public static class BigModelDelta {
        private final String contentDelta;
        private final String reasoningDelta;
        private final boolean done;

        public BigModelDelta(String contentDelta, String reasoningDelta, boolean done) {
            this.contentDelta = contentDelta == null ? "" : contentDelta;
            this.reasoningDelta = reasoningDelta == null ? "" : reasoningDelta;
            this.done = done;
        }

        public static BigModelDelta done() {
            return new BigModelDelta("", "", true);
        }

        public String getContentDelta() {
            return contentDelta;
        }

        public String getReasoningDelta() {
            return reasoningDelta;
        }

        public boolean isDone() {
            return done;
        }
    }
}


