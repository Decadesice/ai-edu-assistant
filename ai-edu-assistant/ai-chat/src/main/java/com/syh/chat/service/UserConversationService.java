package com.syh.chat.service;

import com.syh.chat.model.Message;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserConversationService {
    
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, List<Message>>> userConversations = new ConcurrentHashMap<>();
    
    public List<Message> getConversation(Long userId, String sessionId) {
        return userConversations
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(sessionId, k -> new ArrayList<>());
    }
    
    public void addMessage(Long userId, String sessionId, Message message) {
        userConversations
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(sessionId, k -> new ArrayList<>())
                .add(message);
    }
    
    public String createNewConversation(Long userId) {
        String sessionId = UUID.randomUUID().toString();
        userConversations
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .put(sessionId, new ArrayList<>());
        return sessionId;
    }
    
    public void resetConversation(Long userId, String sessionId) {
        ConcurrentHashMap<String, List<Message>> userSessions = userConversations.get(userId);
        if (userSessions != null) {
            userSessions.remove(sessionId);
        }
    }
    
    public List<String> getUserSessionIds(Long userId) {
        ConcurrentHashMap<String, List<Message>> userSessions = userConversations.get(userId);
        if (userSessions == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(userSessions.keySet());
    }
    
    public List<Message> getMessageFragment(Long userId, String sessionId, int startOrder, int endOrder) {
        List<Message> conversation = getConversation(userId, sessionId);
        if (startOrder < 0 || endOrder >= conversation.size() || startOrder > endOrder) {
            return new ArrayList<>();
        }
        return new ArrayList<>(conversation.subList(startOrder, endOrder + 1));
    }
    
    public void deleteMessage(Long userId, String sessionId, int messageOrder) {
        List<Message> conversation = getConversation(userId, sessionId);
        if (messageOrder >= 0 && messageOrder < conversation.size()) {
            conversation.remove(messageOrder);
        }
    }
    
    public void deleteConversation(Long userId, String sessionId) {
        ConcurrentHashMap<String, List<Message>> userSessions = userConversations.get(userId);
        if (userSessions != null) {
            userSessions.remove(sessionId);
        }
    }
    
    public void deleteAllUserConversations(Long userId) {
        userConversations.remove(userId);
    }
    
    public int getConversationSize(Long userId, String sessionId) {
        return getConversation(userId, sessionId).size();
    }
}

