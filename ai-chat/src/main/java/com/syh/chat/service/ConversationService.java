package com.syh.chat.service;

import com.syh.chat.model.Message;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ConversationService {

    private final ConcurrentMap<String, List<Message>> conversations = new ConcurrentHashMap<>();

    public List<Message> getConversation(String sessionId) {
        return conversations.computeIfAbsent(sessionId, k -> new ArrayList<>());
    }

    public void addMessage(String sessionId, Message message) {
        conversations.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
    }

    public void resetConversation(String sessionId) {
        conversations.remove(sessionId);
    }

    public List<Message> getAllConversations() {
        List<Message> allMessages = new ArrayList<>();
        for (List<Message> conversation : conversations.values()) {
            allMessages.addAll(conversation);
        }
        return allMessages;
    }
}
