package com.syh.chat.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.embedding.Embedding;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChromaVectorStoreService {

    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String tenant;
    private final String database;
    private final String collectionPrefix;
    private final ConcurrentHashMap<String, String> collectionIdCache = new ConcurrentHashMap<>();

    public ChromaVectorStoreService(
            WebClient.Builder webClientBuilder,
            @Value("${chroma.base-url:http://localhost:8000}") String chromaBaseUrl,
            @Value("${chroma.tenant:default_tenant}") String tenant,
            @Value("${chroma.database:default_database}") String database,
            @Value("${chroma.collection-prefix:ollama_chat_doc}") String collectionPrefix
    ) {
        this.webClient = webClientBuilder.baseUrl(Objects.requireNonNull(chromaBaseUrl)).build();
        this.tenant = tenant;
        this.database = database;
        this.collectionPrefix = collectionPrefix;
    }

    public String collectionNameForDocument(Long documentId) {
        return collectionPrefix + "_" + documentId;
    }

    public void upsert(Long documentId, String id, Embedding embedding, String documentText, Map<String, Object> metadata) {
        String collectionName = collectionNameForDocument(documentId);
        String collectionId = ensureCollectionId(collectionName);

        ObjectNode payload = mapper.createObjectNode();
        ArrayNode ids = mapper.createArrayNode();
        ids.add(id);
        payload.set("ids", ids);

        ArrayNode embeddings = mapper.createArrayNode();
        embeddings.add(vectorToJsonArray(embedding));
        payload.set("embeddings", embeddings);

        ArrayNode documents = mapper.createArrayNode();
        documents.add(documentText);
        payload.set("documents", documents);

        ArrayNode metadatas = mapper.createArrayNode();
        metadatas.add(mapper.valueToTree(metadata));
        payload.set("metadatas", metadatas);

        webClient.post()
                .uri("/api/v2/tenants/{tenant}/databases/{database}/collections/{collectionId}/upsert", tenant, database, collectionId)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }

    public List<String> queryDocuments(Long documentId, Embedding queryEmbedding, int topK) {
        String collectionName = collectionNameForDocument(documentId);
        String collectionId = ensureCollectionId(collectionName);

        ObjectNode payload = mapper.createObjectNode();
        ArrayNode queryEmbeddings = mapper.createArrayNode();
        queryEmbeddings.add(vectorToJsonArray(queryEmbedding));
        payload.set("query_embeddings", queryEmbeddings);
        payload.put("n_results", Math.max(1, Math.min(topK, 8)));
        ArrayNode include = mapper.createArrayNode();
        include.add("documents");
        include.add("distances");
        payload.set("include", include);

        String body = webClient.post()
                .uri("/api/v2/tenants/{tenant}/databases/{database}/collections/{collectionId}/query", tenant, database, collectionId)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return extractFirstDocuments(body);
    }

    private String ensureCollectionId(String collectionName) {
        String cached = collectionIdCache.get(collectionName);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        synchronized (collectionIdCache) {
            String cachedAgain = collectionIdCache.get(collectionName);
            if (cachedAgain != null && !cachedAgain.isBlank()) {
                return cachedAgain;
            }
            String id = fetchCollectionIdByName(collectionName);
            if (id == null) {
                id = createCollection(collectionName);
            }
            collectionIdCache.put(collectionName, id);
            return id;
        }
    }

    private String fetchCollectionIdByName(String collectionName) {
        try {
            String body = webClient.get()
                    .uri("/api/v2/tenants/{tenant}/databases/{database}/collections", tenant, database)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            if (body == null || body.isBlank()) return null;
            JsonNode node = mapper.readTree(body);
            if (!node.isArray()) return null;
            for (JsonNode c : node) {
                if (collectionName.equals(c.path("name").asText())) {
                    String id = c.path("id").asText(null);
                    if (id != null && !id.isBlank()) return id;
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private String createCollection(String collectionName) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("name", collectionName);
            String body = webClient.post()
                    .uri("/api/v2/tenants/{tenant}/databases/{database}/collections", tenant, database)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            JsonNode node = mapper.readTree(body);
            String id = node.path("id").asText(null);
            if (id == null || id.isBlank()) {
                throw new IllegalStateException("chroma collection 创建失败");
            }
            return id;
        } catch (Exception e) {
            throw new IllegalStateException("chroma collection 创建失败");
        }
    }

    private ArrayNode vectorToJsonArray(Embedding embedding) {
        ArrayNode arr = mapper.createArrayNode();
        for (Float v : embedding.vector()) {
            arr.add(v);
        }
        return arr;
    }

    private List<String> extractFirstDocuments(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return List.of();
        try {
            JsonNode node = mapper.readTree(rawJson);
            JsonNode docs = node.path("documents");
            if (!docs.isArray() || docs.size() == 0) return List.of();
            JsonNode first = docs.get(0);
            if (!first.isArray()) return List.of();
            List<String> out = new ArrayList<>();
            for (JsonNode d : first) {
                if (d != null && !d.isNull()) {
                    String text = d.asText("");
                    if (!text.isBlank()) out.add(text);
                }
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}


