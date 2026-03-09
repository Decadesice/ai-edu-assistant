package com.syh.chat.service;

import com.syh.chat.entity.Conversation;
import com.syh.chat.entity.MessageEntity;
import com.syh.chat.model.Message;
import com.syh.chat.repository.ConversationRepository;
import com.syh.chat.repository.MessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class DatabaseConversationService {
    
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    
    public DatabaseConversationService(ConversationRepository conversationRepository, MessageRepository messageRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
    }
    
    @Transactional
    public String createConversation(Long userId, String title, String modelName) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setSessionId(UUID.randomUUID().toString());
        conversation.setTitle(title);
        conversation.setModelName(modelName);
        conversation.setIsActive(true);
        
        conversation = conversationRepository.save(conversation);
        return conversation.getSessionId();
    }

    @Transactional
    public void ensureConversation(Long userId, String sessionId, String title, String modelName) {
        Optional<Conversation> conversationOpt = conversationRepository.findBySessionId(sessionId);
        if (conversationOpt.isPresent()) {
            Conversation conversation = conversationOpt.get();
            if (!conversation.getUserId().equals(userId)) {
                throw new IllegalArgumentException("无权访问该对话");
            }
            return;
        }

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setSessionId(sessionId);
        conversation.setTitle(title);
        conversation.setModelName(modelName);
        conversation.setIsActive(true);
        conversationRepository.save(conversation);
    }
    
    public Optional<Conversation> getConversationBySessionId(String sessionId) {
        return conversationRepository.findBySessionId(sessionId);
    }
    
    public List<Conversation> getUserConversations(Long userId) {
        return conversationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }
    
    public List<Conversation> getActiveUserConversations(Long userId) {
        return conversationRepository.findByUserIdAndIsActiveOrderByCreatedAtDesc(userId, true);
    }
    
    @Transactional
    public void saveMessage(Long userId, String sessionId, Message message) {
        Optional<Conversation> conversationOpt = conversationRepository.findBySessionId(sessionId);
        if (conversationOpt.isEmpty()) {
            throw new IllegalArgumentException("对话不存在");
        }
        
        Conversation conversation = conversationOpt.get();
        if (!conversation.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该对话");
        }
        
        int messageOrder = messageRepository.findByConversationIdOrderByMessageOrderAsc(conversation.getId()).size();
        
        MessageEntity messageEntity = new MessageEntity();
        messageEntity.setConversationId(conversation.getId());
        messageEntity.setUserId(userId);
        messageEntity.setRole(message.getRole());
        messageEntity.setContent(message.getContent());
        String imageUrl = message.getImages() != null && !message.getImages().isEmpty() ? message.getImages().get(0) : null;
        messageEntity.setImageUrl(imageUrl);
        messageEntity.setMessageOrder(messageOrder);
        
        messageRepository.save(messageEntity);
    }

    @Transactional
    public void updateConversationTitleIfDefault(Long userId, String sessionId, String newTitle) {
        if (newTitle == null || newTitle.isBlank()) {
            return;
        }
        Optional<Conversation> conversationOpt = conversationRepository.findBySessionId(sessionId);
        if (conversationOpt.isEmpty()) {
            return;
        }
        Conversation conversation = conversationOpt.get();
        if (!conversation.getUserId().equals(userId)) {
            return;
        }
        String oldTitle = conversation.getTitle();
        if (oldTitle == null || oldTitle.isBlank() || "新对话".equals(oldTitle)) {
            conversation.setTitle(newTitle);
            conversationRepository.save(conversation);
        }
    }

    public boolean isConversationTitleDefault(Long userId, String sessionId) {
        Optional<Conversation> conversationOpt = conversationRepository.findBySessionId(sessionId);
        if (conversationOpt.isEmpty()) {
            return false;
        }
        Conversation conversation = conversationOpt.get();
        if (!conversation.getUserId().equals(userId)) {
            return false;
        }
        String title = conversation.getTitle();
        return title == null || title.isBlank() || "新对话".equals(title);
    }
    
    public List<Message> getConversationMessages(Long userId, String sessionId) {
        Optional<Conversation> conversationOpt = conversationRepository.findBySessionId(sessionId);
        if (conversationOpt.isEmpty()) {
            return new ArrayList<>();
        }
        
        Conversation conversation = conversationOpt.get();
        if (!conversation.getUserId().equals(userId)) {
            return new ArrayList<>();
        }
        
        List<MessageEntity> messageEntities = messageRepository.findByConversationIdOrderByMessageOrderAsc(conversation.getId());
        List<Message> messages = new ArrayList<>();
        
        for (MessageEntity entity : messageEntities) {
            Message message = new Message(entity.getRole(), entity.getContent());
            if (entity.getImageUrl() != null) {
                message.setImages(List.of(entity.getImageUrl()));
            }
            messages.add(message);
        }
        
        return messages;
    }
    
    public List<Message> getMessageFragment(Long userId, String sessionId, int startOrder, int endOrder) {
        Optional<Conversation> conversationOpt = conversationRepository.findBySessionId(sessionId);
        if (conversationOpt.isEmpty()) {
            return new ArrayList<>();
        }
        
        Conversation conversation = conversationOpt.get();
        if (!conversation.getUserId().equals(userId)) {
            return new ArrayList<>();
        }
        
        List<MessageEntity> messageEntities = messageRepository.findByConversationIdAndMessageOrderBetweenOrderByMessageOrderAsc(
                conversation.getId(), startOrder, endOrder);
        
        List<Message> messages = new ArrayList<>();
        for (MessageEntity entity : messageEntities) {
            Message message = new Message(entity.getRole(), entity.getContent());
            if (entity.getImageUrl() != null) {
                message.setImages(List.of(entity.getImageUrl()));
            }
            messages.add(message);
        }
        
        return messages;
    }
    
    @Transactional
    public void deleteMessage(Long userId, String sessionId, int messageOrder) {
        Optional<Conversation> conversationOpt = conversationRepository.findBySessionId(sessionId);
        if (conversationOpt.isEmpty()) {
            return;
        }
        
        Conversation conversation = conversationOpt.get();
        if (!conversation.getUserId().equals(userId)) {
            return;
        }
        
        messageRepository.deleteByConversationIdAndMessageOrder(conversation.getId(), messageOrder);
    }
    
    @Transactional
    public void deleteConversation(Long userId, String sessionId) {
        Optional<Conversation> conversationOpt = conversationRepository.findBySessionId(sessionId);
        if (conversationOpt.isEmpty()) {
            return;
        }
        
        Conversation conversation = conversationOpt.get();
        if (!conversation.getUserId().equals(userId)) {
            return;
        }
        
        messageRepository.deleteByConversationId(conversation.getId());
        conversationRepository.delete(conversation);
    }
    
    @Transactional
    public void deleteAllUserConversations(Long userId) {
        List<Conversation> conversations = conversationRepository.findByUserIdOrderByCreatedAtDesc(userId);
        for (Conversation conversation : conversations) {
            messageRepository.deleteByConversationId(conversation.getId());
        }
        conversationRepository.deleteAll(Objects.requireNonNull(conversations));
    }
    
    @Transactional
    public void updateConversationTitle(Long userId, String sessionId, String title) {
        Optional<Conversation> conversationOpt = conversationRepository.findBySessionId(sessionId);
        if (conversationOpt.isEmpty()) {
            return;
        }
        
        Conversation conversation = conversationOpt.get();
        if (!conversation.getUserId().equals(userId)) {
            return;
        }
        
        conversation.setTitle(title);
        conversationRepository.save(conversation);
    }
    
    public int getConversationMessageCount(Long userId, String sessionId) {
        Optional<Conversation> conversationOpt = conversationRepository.findBySessionId(sessionId);
        if (conversationOpt.isEmpty()) {
            return 0;
        }
        
        Conversation conversation = conversationOpt.get();
        if (!conversation.getUserId().equals(userId)) {
            return 0;
        }
        
        return messageRepository.findByConversationIdOrderByMessageOrderAsc(conversation.getId()).size();
    }
}

