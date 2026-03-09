package com.syh.chat.service;

import com.syh.chat.entity.Conversation;
import com.syh.chat.entity.MessageEntity;
import com.syh.chat.model.Message;
import com.syh.chat.repository.ConversationRepository;
import com.syh.chat.repository.MessageRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.*;

public class DatabaseConversationServiceTest {

    @SuppressWarnings("null")
    @Test
    void saveMessage_shouldPersistBase64Image() {
        ConversationRepository conversationRepository = mock(ConversationRepository.class);
        MessageRepository messageRepository = mock(MessageRepository.class);
        DatabaseConversationService service = new DatabaseConversationService(conversationRepository, messageRepository);

        Conversation conversation = new Conversation();
        conversation.setId(1L);
        conversation.setUserId(10L);
        conversation.setSessionId("s1");

        when(conversationRepository.findBySessionId("s1")).thenReturn(Optional.of(conversation));
        when(messageRepository.findByConversationIdOrderByMessageOrderAsc(1L)).thenReturn(List.of());
        Message message = new Message("user", "hi");
        message.setImages(List.of("data:image/png;base64,AAAA"));

        when(messageRepository.save(notNull())).thenAnswer(inv -> {
            MessageEntity saved = inv.getArgument(0, MessageEntity.class);
            assertEquals("data:image/png;base64,AAAA", saved.getImageUrl());
            return saved;
        });

        service.saveMessage(10L, "s1", message);

        verify(messageRepository).save(notNull());
    }
}


