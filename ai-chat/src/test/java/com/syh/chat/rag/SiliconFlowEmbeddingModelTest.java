package com.syh.chat.rag;

import com.syh.chat.config.SiliconFlowProperties;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SiliconFlowEmbeddingModelTest {

    private DisposableServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.disposeNow();
        }
    }

    @Test
    void embed_parsesFloatVector() {
        server = HttpServer.create()
                .port(0)
                .route(routes -> routes.post("/embeddings", (req, resp) -> resp
                        .header("Content-Type", "application/json")
                        .sendString(Mono.just("""
                                {
                                  "object": "list",
                                  "model": "BAAI/bge-m3",
                                  "data": [
                                    { "object": "embedding", "index": 0, "embedding": [0.1, 0.2, 0.3] }
                                  ],
                                  "usage": { "prompt_tokens": 3, "total_tokens": 3 }
                                }
                                """))))
                .bindNow();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + server.port())
                .build();
        SiliconFlowProperties props = new SiliconFlowProperties();
        props.setApiKey("test-key");

        SiliconFlowEmbeddingModel model = new SiliconFlowEmbeddingModel(webClient, props, "BAAI/bge-m3");
        Embedding embedding = model.embed("hi").content();
        assertArrayEquals(new float[]{0.1f, 0.2f, 0.3f}, embedding.vector(), 0.00001f);
    }

    @Test
    void embedAll_keepsOrdering() {
        server = HttpServer.create()
                .port(0)
                .route(routes -> routes.post("/embeddings", (req, resp) -> resp
                        .header("Content-Type", "application/json")
                        .sendString(Mono.just("""
                                {
                                  "object": "list",
                                  "model": "BAAI/bge-m3",
                                  "data": [
                                    { "object": "embedding", "index": 0, "embedding": [1.0, 1.1] },
                                    { "object": "embedding", "index": 1, "embedding": [2.0, 2.2] }
                                  ],
                                  "usage": { "prompt_tokens": 3, "total_tokens": 3 }
                                }
                                """))))
                .bindNow();

        WebClient webClient = WebClient.builder()
                .baseUrl("http://127.0.0.1:" + server.port())
                .build();
        SiliconFlowProperties props = new SiliconFlowProperties();
        props.setApiKey("test-key");

        SiliconFlowEmbeddingModel model = new SiliconFlowEmbeddingModel(webClient, props, "BAAI/bge-m3");
        List<Embedding> embeddings = model.embedAll(List.of(TextSegment.from("a"), TextSegment.from("b"))).content();
        assertEquals(2, embeddings.size());
        assertArrayEquals(new float[]{1.0f, 1.1f}, embeddings.get(0).vector(), 0.00001f);
        assertArrayEquals(new float[]{2.0f, 2.2f}, embeddings.get(1).vector(), 0.00001f);
    }
}

