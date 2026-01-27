package com.syh.chat.config;

import com.syh.chat.rag.SiliconFlowEmbeddingModel;
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
            SiliconFlowProperties siliconFlowProperties,
            @Value("${siliconflow.embedding-model:BAAI/bge-m3}") String siliconFlowEmbeddingModelName
    ) {
        WebClient webClient = webClientBuilder.baseUrl(Objects.requireNonNull(siliconFlowProperties.getBaseUrl())).build();
        return new SiliconFlowEmbeddingModel(webClient, siliconFlowProperties, siliconFlowEmbeddingModelName);
    }
}

