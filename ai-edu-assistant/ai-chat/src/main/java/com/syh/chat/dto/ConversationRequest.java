package com.syh.chat.dto;

import lombok.Data;

@Data
public class ConversationRequest {
    private String sessionId;
    private String title;
    private String modelName;
}

