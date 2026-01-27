package com.syh.chat.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

@Configuration
@EnableConfigurationProperties(BigModelProperties.class)
public class BigModelConfig {

    @Bean
    @Qualifier("bigModelWebClient")
    public WebClient bigModelWebClient(BigModelProperties properties) {
        return WebClient.builder()
                .baseUrl(Objects.requireNonNull(properties.getBaseUrl()))
                .build();
    }
}


