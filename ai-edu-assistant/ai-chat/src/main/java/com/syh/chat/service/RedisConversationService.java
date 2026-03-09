package com.syh.chat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.syh.chat.model.Message;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Service
public class RedisConversationService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String USER_CONVERSATION_PREFIX = "user:conversation:";
    private static final String USER_SESSION_PREFIX = "user:session:";
    private static final long DEFAULT_TTL_HOURS = 24;

    public RedisConversationService(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private String getUserConversationKey(Long userId, String sessionId) {
        return USER_CONVERSATION_PREFIX + userId + ":" + sessionId;
    }

    private String getUserSessionsKey(Long userId) {
        return USER_SESSION_PREFIX + userId;
    }

    public List<Message> getConversation(Long userId, String sessionId) {
        String key = Objects.requireNonNull(getUserConversationKey(userId, sessionId));
        Object data = redisTemplate.opsForValue().get(Objects.requireNonNull(key));

        if (data == null) {
            return new ArrayList<>();
        }

        try {
            if (data instanceof String) {
                return objectMapper.readValue((String) data, new TypeReference<List<Message>>() {
                });
            } else if (data instanceof List) {
                List<Message> result = new ArrayList<>();
                for (Object item : (List<?>) data) {
                    if (item instanceof Message) {
                        result.add((Message) item);
                    }
                }
                return result;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new ArrayList<>();
    }

    public void saveConversation(Long userId, String sessionId, List<Message> messages) {
        String key = Objects.requireNonNull(getUserConversationKey(userId, sessionId));
        try {
            String json = Objects.requireNonNull(objectMapper.writeValueAsString(messages));
            redisTemplate.opsForValue().set(Objects.requireNonNull(key), Objects.requireNonNull(json), DEFAULT_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addMessage(Long userId, String sessionId, Message message) {
        List<Message> conversation = getConversation(userId, sessionId);
        conversation.add(message);
        saveConversation(userId, sessionId, conversation);
    }

    public void addSessionToUser(Long userId, String sessionId) {
        String sessionsKey = Objects.requireNonNull(getUserSessionsKey(userId));
        redisTemplate.opsForSet().add(Objects.requireNonNull(sessionsKey), Objects.requireNonNull(sessionId));
        redisTemplate.expire(Objects.requireNonNull(sessionsKey), DEFAULT_TTL_HOURS, TimeUnit.HOURS);
    }

    public List<String> getUserSessions(Long userId) {
        String sessionsKey = Objects.requireNonNull(getUserSessionsKey(userId));
        java.util.Set<Object> members = redisTemplate.opsForSet().members(Objects.requireNonNull(sessionsKey));
        List<String> sessions = new ArrayList<>();
        if (members != null) {
            for (Object member : members) {
                if (member != null) {
                    sessions.add(member.toString());
                }
            }
        }
        return sessions;
    }

    public void deleteConversation(Long userId, String sessionId) {
        String key = Objects.requireNonNull(getUserConversationKey(userId, sessionId));
        redisTemplate.delete(Objects.requireNonNull(key));

        String sessionsKey = Objects.requireNonNull(getUserSessionsKey(userId));
        redisTemplate.opsForSet().remove(Objects.requireNonNull(sessionsKey), Objects.requireNonNull(sessionId));
    }

    public void deleteAllUserConversations(Long userId) {
        List<String> sessions = getUserSessions(userId);
        for (String sessionId : sessions) {
            String key = Objects.requireNonNull(getUserConversationKey(userId, sessionId));
            redisTemplate.delete(Objects.requireNonNull(key));
        }

        String sessionsKey = Objects.requireNonNull(getUserSessionsKey(userId));
        redisTemplate.delete(Objects.requireNonNull(sessionsKey));
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
            saveConversation(userId, sessionId, conversation);
        }
    }

    public void updateMessage(Long userId, String sessionId, int messageOrder, Message newMessage) {
        List<Message> conversation = getConversation(userId, sessionId);
        if (messageOrder >= 0 && messageOrder < conversation.size()) {
            conversation.set(messageOrder, newMessage);
            saveConversation(userId, sessionId, conversation);
        }
    }

    public boolean conversationExists(Long userId, String sessionId) {
        String key = Objects.requireNonNull(getUserConversationKey(userId, sessionId));
        return Boolean.TRUE.equals(redisTemplate.hasKey(Objects.requireNonNull(key)));
    }

    public void extendTTL(Long userId, String sessionId) {
        String key = Objects.requireNonNull(getUserConversationKey(userId, sessionId));
        redisTemplate.expire(Objects.requireNonNull(key), DEFAULT_TTL_HOURS, TimeUnit.HOURS);
    }
}

