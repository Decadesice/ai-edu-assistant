package com.syh.chat.dto;

import lombok.Data;

@Data
public class MessageFragmentRequest {
    private String sessionId;
    private Integer startOrder;
    private Integer endOrder;
}

