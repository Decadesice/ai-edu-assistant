package com.syh.chat.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "message")
public class MessageEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;
    
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    @Column(nullable = false, length = 20)
    private String role;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(name = "image_url", columnDefinition = "LONGTEXT")
    private String imageUrl;
    
    @Column(name = "message_order")
    private Integer messageOrder;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

