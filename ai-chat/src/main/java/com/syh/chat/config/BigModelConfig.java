package com.syh.chat.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

@Configuration
@EnableConfigurationProperties(BigModelProperties.class)
public class BigModelConfig {

    @Bean
    @Qualifier("bigModelWebClient")
    public WebClient bigModelWebClient(BigModelProperties properties, WebClient.Builder webClientBuilder) {
        return webClientBuilder
                .baseUrl(Objects.requireNonNull(properties.getBaseUrl()))
                .build();
    }

    @Bean
    @Qualifier("bigModelLongWebClient")
    public WebClient bigModelLongWebClient(
            BigModelProperties properties,
            WebClient.Builder webClientBuilder,
            @Qualifier("longReactorClientHttpConnector") ReactorClientHttpConnector longConnector
    ) {
        return webClientBuilder
                .clientConnector(Objects.requireNonNull(longConnector))
                .baseUrl(Objects.requireNonNull(properties.getBaseUrl()))
                .build();
    }
}


