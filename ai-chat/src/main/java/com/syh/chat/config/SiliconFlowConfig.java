package com.syh.chat.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Objects;

@Configuration
@EnableConfigurationProperties(SiliconFlowProperties.class)
public class SiliconFlowConfig {

    @Bean
    @Qualifier("siliconFlowWebClient")
    public WebClient siliconFlowWebClient(SiliconFlowProperties properties) {
        return WebClient.builder()
                .baseUrl(Objects.requireNonNull(properties.getBaseUrl()))
                .build();
    }
}


