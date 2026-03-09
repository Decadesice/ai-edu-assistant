package com.syh.chat.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syh.chat.config.OllamaProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class OllamaEmbeddingModel implements EmbeddingModel {

    private final WebClient webClient;
    private final OllamaProperties properties;
    private final String modelName;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OllamaEmbeddingModel(WebClient webClient, OllamaProperties properties, String modelName) {
        this.webClient = Objects.requireNonNull(webClient);
        this.properties = Objects.requireNonNull(properties);
        this.modelName = modelName == null ? "" : modelName.trim();
    }

    @Override
    public Response<Embedding> embed(String text) {
        Embedding embedding = embedInternal(text);
        return Response.from(embedding);
    }

    @Override
    public Response<Embedding> embed(TextSegment textSegment) {
        String text = textSegment == null ? "" : textSegment.text();
        Embedding embedding = embedInternal(text);
        return Response.from(embedding);
    }

    @Override
    public Response<List<Embedding>> embedAll(List<TextSegment> textSegments) {
        if (textSegments == null || textSegments.isEmpty()) {
            return Response.from(List.of());
        }

        List<String> inputs = new ArrayList<>(textSegments.size());
        for (TextSegment seg : textSegments) {
            inputs.add(seg == null ? "" : seg.text());
        }

        List<Embedding> embeddings = embedBatchInternal(inputs);
        return Response.from(embeddings);
    }

    private Embedding embedInternal(String input) {
        List<Embedding> out = embedBatchInternal(List.of(input == null ? "" : input));
        if (out.isEmpty()) {
            throw new IllegalStateException("Ollama embeddings 返回为空");
        }
        return out.get(0);
    }

    private List<Embedding> embedBatchInternal(List<String> inputs) {
        List<Embedding> result = new ArrayList<>();
        for (String input : inputs) {
            result.add(embedSingle(input));
        }
        return result;
    }

    private Embedding embedSingle(String input) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName.isBlank() ? properties.getEmbeddingModel() : modelName);
        ArrayNode inputArray = objectMapper.createArrayNode();
        inputArray.add(input == null ? "" : input);
        body.set("input", inputArray);

        String raw = webClient.post()
                .uri("/api/embed")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), resp -> resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new IllegalStateException("Ollama embeddings 调用失败: HTTP " + resp.statusCode().value() + " " + b)))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(120))
                .block();

        return parseEmbedding(raw);
    }

    private Embedding parseEmbedding(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalStateException("Ollama embeddings 返回为空");
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            
            JsonNode errorNode = root.path("error");
            if (!errorNode.isMissingNode() && !errorNode.isNull()) {
                String errorMsg = errorNode.asText();
                throw new IllegalStateException("Ollama embeddings 错误: " + (errorMsg == null || errorMsg.isBlank() ? "未知错误" : errorMsg));
            }
            
            JsonNode emb = root.path("embeddings");
            if (emb.isArray() && emb.size() > 0) {
                JsonNode firstEmb = emb.get(0);
                if (firstEmb.isArray() && firstEmb.size() > 0) {
                    List<Float> vector = new ArrayList<>(firstEmb.size());
                    for (JsonNode v : firstEmb) {
                        vector.add((float) v.asDouble());
                    }
                    return Embedding.from(vector);
                }
            }
            
            emb = root.path("embedding");
            if (emb.isArray() && emb.size() > 0) {
                List<Float> vector = new ArrayList<>(emb.size());
                for (JsonNode v : emb) {
                    vector.add((float) v.asDouble());
                }
                return Embedding.from(vector);
            }
            
            throw new IllegalStateException("Ollama embeddings 返回缺少 embedding。响应: " + truncate(rawJson, 500));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Ollama embeddings 解析失败: " + e.getMessage());
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
}
