package com.syh.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiChatApplication.class, args);
    }
}
