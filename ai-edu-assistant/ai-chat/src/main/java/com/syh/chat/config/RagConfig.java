package com.syh.chat.config;

import com.syh.chat.rag.OllamaEmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

@Configuration
public class RagConfig {

    @Bean
    @Lazy
    public EmbeddingModel embeddingModel(
            WebClient.Builder webClientBuilder,
            OllamaProperties ollamaProperties,
            @Value("${ollama.embedding-model:bge-m3}") String ollamaEmbeddingModelName
    ) {
        WebClient webClient = webClientBuilder.baseUrl(Objects.requireNonNull(ollamaProperties.getBaseUrl())).build();
        return new OllamaEmbeddingModel(webClient, ollamaProperties, ollamaEmbeddingModelName);
    }
}

