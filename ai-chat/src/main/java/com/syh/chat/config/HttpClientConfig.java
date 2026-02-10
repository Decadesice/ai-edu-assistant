package com.syh.chat.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.reactive.function.client.WebClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Objects;

@Configuration
public class HttpClientConfig {

    @Bean
    public ReactorClientHttpConnector reactorClientHttpConnector(
            @Value("${app.http.client.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${app.http.client.response-timeout-ms:15000}") long responseTimeoutMs
    ) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(responseTimeoutMs));
        return new ReactorClientHttpConnector(Objects.requireNonNull(httpClient));
    }

    @Bean
    public WebClientCustomizer webClientCustomizer(ReactorClientHttpConnector connector) {
        return builder -> builder.clientConnector(Objects.requireNonNull(connector));
    }
}
