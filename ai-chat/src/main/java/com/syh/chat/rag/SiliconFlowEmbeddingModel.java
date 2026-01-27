package com.syh.chat.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.syh.chat.config.SiliconFlowProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SiliconFlowEmbeddingModel implements EmbeddingModel {

    private final WebClient webClient;
    private final SiliconFlowProperties properties;
    private final String modelName;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SiliconFlowEmbeddingModel(WebClient webClient, SiliconFlowProperties properties, String modelName) {
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
            throw new IllegalStateException("SiliconFlow embeddings 返回为空");
        }
        return out.get(0);
    }

    private List<Embedding> embedBatchInternal(List<String> inputs) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new IllegalStateException("SiliconFlow API Key 未配置，请设置环境变量 SiliconFlow_Api_Key / SILICONFLOW_API_KEY 或 siliconflow.api-key");
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", modelName.isBlank() ? "BAAI/bge-m3" : modelName);
        ArrayNode arr = objectMapper.createArrayNode();
        for (String s : inputs) {
            arr.add(s == null ? "" : s);
        }
        body.set("input", arr);
        body.put("encoding_format", "float");

        String raw = webClient.post()
                .uri("/embeddings")
                .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.getApiKey())
                .bodyValue(body)
                .retrieve()
                .onStatus(status -> status.isError(), resp -> resp.bodyToMono(String.class)
                        .defaultIfEmpty("")
                        .map(b -> new IllegalStateException("SiliconFlow embeddings 调用失败: HTTP " + resp.statusCode().value() + " " + b)))
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(120))
                .block();

        return parseEmbeddings(raw);
    }

    private List<Embedding> parseEmbeddings(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new IllegalStateException("SiliconFlow embeddings 返回为空");
        }
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() == 0) {
                throw new IllegalStateException("SiliconFlow embeddings 返回缺少 data");
            }

            List<Embedding> out = new ArrayList<>(data.size());
            for (JsonNode item : data) {
                JsonNode emb = item.path("embedding");
                if (!emb.isArray() || emb.size() == 0) {
                    throw new IllegalStateException("SiliconFlow embeddings 返回缺少 embedding");
                }
                List<Float> vector = new ArrayList<>(emb.size());
                for (JsonNode v : emb) {
                    vector.add((float) v.asDouble());
                }
                out.add(Embedding.from(vector));
            }
            return out;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("SiliconFlow embeddings 解析失败: " + e.getMessage());
        }
    }
}


