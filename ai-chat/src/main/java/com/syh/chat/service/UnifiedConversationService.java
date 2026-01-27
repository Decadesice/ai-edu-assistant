package com.syh.chat.service;

import com.syh.chat.model.Message;
import com.syh.chat.dto.ConversationSummaryResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

@Service
public class UnifiedConversationService {

    private final RedisConversationService redisConversationService;
    private final DatabaseConversationService databaseConversationService;
    private final BigModelService bigModelService;
    private final SiliconFlowService siliconFlowService;

    public UnifiedConversationService(
            RedisConversationService redisConversationService,
            DatabaseConversationService databaseConversationService,
            BigModelService bigModelService,
            SiliconFlowService siliconFlowService) {
        this.redisConversationService = redisConversationService;
        this.databaseConversationService = databaseConversationService;
        this.bigModelService = bigModelService;
        this.siliconFlowService = siliconFlowService;
    }

    private void ensureConversationExists(Long userId, String sessionId, String title, String modelName) {
        databaseConversationService.ensureConversation(userId, sessionId, title, modelName);
        redisConversationService.addSessionToUser(userId, sessionId);
    }

    private String normalizeChatModel(String modelName) {
        if (modelName == null) {
            return "glm-4.6v-Flash";
        }
        String m = modelName.trim();
        if (m.isEmpty()) {
            return "glm-4.6v-Flash";
        }
        if ("glm-4.6v-Flash".equalsIgnoreCase(m)) {
            return "glm-4.6v-Flash";
        }
        if ("GLM-4.1V-9B-Thinking".equalsIgnoreCase(m)
                || "THUDM/GLM-4.1V-9B-Thinking".equalsIgnoreCase(m)
                || "glm-4.1v-9b-thinking".equalsIgnoreCase(m)) {
            return "THUDM/GLM-4.1V-9B-Thinking";
        }
        return "glm-4.6v-Flash";
    }

    public List<Message> getConversation(Long userId, String sessionId) {
        List<Message> conversation = redisConversationService.getConversation(userId, sessionId);

        if (conversation.isEmpty()) {
            conversation = databaseConversationService.getConversationMessages(userId, sessionId);
            if (!conversation.isEmpty()) {
                redisConversationService.saveConversation(userId, sessionId, conversation);
            }
        }

        return conversation;
    }

    public void addMessage(Long userId, String sessionId, Message message) {
        redisConversationService.addMessage(userId, sessionId, message);
        databaseConversationService.saveMessage(userId, sessionId, message);
    }

    public String createNewConversation(Long userId, String title, String modelName) {
        String sessionId = databaseConversationService.createConversation(userId, title, normalizeChatModel(modelName));
        redisConversationService.addSessionToUser(userId, sessionId);
        return sessionId;
    }

    public void continueFromHistory(Long userId, String sessionId) {
        List<Message> history = databaseConversationService.getConversationMessages(userId, sessionId);
        if (!history.isEmpty()) {
            redisConversationService.saveConversation(userId, sessionId, history);
        }
    }

    public void continueFromFragment(Long userId, String sessionId, int startOrder, int endOrder) {
        List<Message> fragment = databaseConversationService.getMessageFragment(userId, sessionId, startOrder,
                endOrder);
        if (!fragment.isEmpty()) {
            redisConversationService.saveConversation(userId, sessionId, fragment);
        }
    }

    public void deleteConversation(Long userId, String sessionId) {
        redisConversationService.deleteConversation(userId, sessionId);
        databaseConversationService.deleteConversation(userId, sessionId);
    }

    public void deleteMessage(Long userId, String sessionId, int messageOrder) {
        redisConversationService.deleteMessage(userId, sessionId, messageOrder);
        databaseConversationService.deleteMessage(userId, sessionId, messageOrder);
    }

    public Flux<String> chatWithStream(Long userId, String sessionId, String userMessage, String modelName,
            String image) {
        String effectiveModelName = normalizeChatModel(modelName);
        ensureConversationExists(userId, sessionId, "新对话", effectiveModelName);
        Message message = new Message("user", userMessage);
        if (image != null && !image.isBlank()) {
            message.setImages(java.util.List.of(image));
        }
        addMessage(userId, sessionId, message);

        List<Message> conversation = getConversation(userId, sessionId);

        if ("glm-4.6v-Flash".equalsIgnoreCase(effectiveModelName)) {
            return chatWithBigModel(userId, sessionId, userMessage, effectiveModelName, image, conversation);
        }
        return chatWithSiliconFlow(userId, sessionId, userMessage, effectiveModelName, image, conversation);
    }

    private Flux<String> chatWithBigModel(Long userId, String sessionId, String userMessage, String modelName, String image, List<Message> conversation) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        StringBuilder fullContent = new StringBuilder();
        int chunkSize = 36;

        return bigModelService.chatStream(conversation, modelName)
                .concatMap(delta -> {
                    if (delta.isDone()) {
                        return Flux.just("{\"done\":true}\n");
                    }

                    java.util.ArrayList<String> out = new java.util.ArrayList<>();
                    String thinkingDelta = delta.getReasoningDelta();
                    String contentDelta = delta.getContentDelta();

                    try {
                        if (thinkingDelta != null && !thinkingDelta.isBlank()) {
                            int idx = 0;
                            while (idx < thinkingDelta.length()) {
                                String part = thinkingDelta.substring(idx, Math.min(thinkingDelta.length(), idx + chunkSize));
                                com.fasterxml.jackson.databind.node.ObjectNode t = mapper.createObjectNode();
                                com.fasterxml.jackson.databind.node.ObjectNode m = mapper.createObjectNode();
                                m.put("thinking", part);
                                t.set("message", m);
                                out.add(t.toString() + "\n");
                                idx += chunkSize;
                            }
                        }
                        if (contentDelta != null && !contentDelta.isEmpty()) {
                            fullContent.append(contentDelta);
                            int idx = 0;
                            while (idx < contentDelta.length()) {
                                String part = contentDelta.substring(idx, Math.min(contentDelta.length(), idx + chunkSize));
                                com.fasterxml.jackson.databind.node.ObjectNode c = mapper.createObjectNode();
                                com.fasterxml.jackson.databind.node.ObjectNode m = mapper.createObjectNode();
                                m.put("content", part);
                                c.set("message", m);
                                out.add(c.toString() + "\n");
                                idx += chunkSize;
                            }
                        }
                    } catch (Exception e) {
                        return Flux.empty();
                    }

                    return Flux.fromIterable(out);
                })
                .doOnComplete(() -> {
                    if (!fullContent.isEmpty()) {
                        addMessage(userId, sessionId, new Message("assistant", fullContent.toString()));
                    }
                    generateTitleAsync(userId, sessionId, modelName, userMessage, fullContent.toString(), image != null && !image.isBlank());
                })
                .onErrorResume(e -> {
                    com.fasterxml.jackson.databind.node.ObjectNode err = mapper.createObjectNode();
                    err.put("type", "error");
                    err.put("message", e.getMessage() == null ? "BigModel 调用失败" : e.getMessage());
                    return Flux.just(err.toString() + "\n", "{\"done\":true}\n");
                });
    }

    private Flux<String> chatWithSiliconFlow(Long userId, String sessionId, String userMessage, String modelName, String image, List<Message> conversation) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        StringBuilder fullContent = new StringBuilder();
        int chunkSize = 36;

        return siliconFlowService.chatStream(conversation, modelName)
                .concatMap(delta -> {
                    if (delta.isDone()) {
                        return Flux.just("{\"done\":true}\n");
                    }

                    java.util.ArrayList<String> out = new java.util.ArrayList<>();
                    String thinkingDelta = delta.getReasoningDelta();
                    String contentDelta = delta.getContentDelta();

                    try {
                        if (thinkingDelta != null && !thinkingDelta.isBlank()) {
                            int idx = 0;
                            while (idx < thinkingDelta.length()) {
                                String part = thinkingDelta.substring(idx, Math.min(thinkingDelta.length(), idx + chunkSize));
                                com.fasterxml.jackson.databind.node.ObjectNode t = mapper.createObjectNode();
                                com.fasterxml.jackson.databind.node.ObjectNode m = mapper.createObjectNode();
                                m.put("thinking", part);
                                t.set("message", m);
                                out.add(t.toString() + "\n");
                                idx += chunkSize;
                            }
                        }
                        if (contentDelta != null && !contentDelta.isEmpty()) {
                            fullContent.append(contentDelta);
                            int idx = 0;
                            while (idx < contentDelta.length()) {
                                String part = contentDelta.substring(idx, Math.min(contentDelta.length(), idx + chunkSize));
                                com.fasterxml.jackson.databind.node.ObjectNode c = mapper.createObjectNode();
                                com.fasterxml.jackson.databind.node.ObjectNode m = mapper.createObjectNode();
                                m.put("content", part);
                                c.set("message", m);
                                out.add(c.toString() + "\n");
                                idx += chunkSize;
                            }
                        }
                    } catch (Exception e) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(out);
                })
                .doOnComplete(() -> {
                    if (!fullContent.isEmpty()) {
                        addMessage(userId, sessionId, new Message("assistant", fullContent.toString()));
                    }
                    generateTitleAsync(userId, sessionId, modelName, userMessage, fullContent.toString(), image != null && !image.isBlank());
                })
                .onErrorResume(e -> {
                    com.fasterxml.jackson.databind.node.ObjectNode err = mapper.createObjectNode();
                    err.put("type", "error");
                    err.put("message", e.getMessage() == null ? "SiliconFlow 调用失败" : e.getMessage());
                    return Flux.just(err.toString() + "\n", "{\"done\":true}\n");
                });
    }

    private void generateTitleAsync(Long userId, String sessionId, String modelName, String userText, String assistantText, boolean hasImage) {
        Mono.fromCallable(() -> {
                    if (!databaseConversationService.isConversationTitleDefault(userId, sessionId)) {
                        return null;
                    }
                    try {
                        String title = bigModelService.generateTitleOnce(userText, assistantText, hasImage, 12).block();
                        if (title == null || title.isBlank()) {
                            throw new IllegalStateException("empty-title");
                        }
                        databaseConversationService.updateConversationTitleIfDefault(userId, sessionId, title);
                        return title;
                    } catch (Exception ignore) {
                        String title = buildFallbackTitle(userText, hasImage);
                        databaseConversationService.updateConversationTitleIfDefault(userId, sessionId, title);
                        return title;
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }

    private String buildFallbackTitle(String userMessage, boolean hasImage) {
        String base = userMessage == null ? "" : userMessage;
        base = base.replaceAll("\\s+", " ").trim();
        if (base.isEmpty()) {
            if (hasImage) {
                return "图片对话";
            }
            return null;
        }
        int endIndex = base.length();
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?' || c == '\n' || c == '\r') {
                endIndex = i;
                break;
            }
        }
        String title = base.substring(0, endIndex).trim();
        if (title.isEmpty()) {
            title = base;
        }
        int maxLen = 18;
        if (title.length() > maxLen) {
            title = title.substring(0, maxLen);
        }
        return title;
    }

    public List<String> getUserConversations(Long userId) {
        return databaseConversationService.getUserConversations(userId).stream()
                .map(conv -> conv.getSessionId())
                .toList();
    }

    public List<ConversationSummaryResponse> getUserConversationSummaries(Long userId) {
        return databaseConversationService.getUserConversations(userId).stream()
                .map(conv -> new ConversationSummaryResponse(
                        conv.getSessionId(),
                        conv.getTitle() != null && !conv.getTitle().isBlank() ? conv.getTitle() : "新对话",
                        conv.getModelName(),
                        conv.getUpdatedAt() != null ? conv.getUpdatedAt() : conv.getCreatedAt()
                ))
                .toList();
    }

    public List<Message> getMessageFragment(Long userId, String sessionId, int startOrder, int endOrder) {
        return databaseConversationService.getMessageFragment(userId, sessionId, startOrder, endOrder);
    }

    public String summarizeConversation(Long userId, String sessionId, String modelName) {
        String effectiveModelName = normalizeChatModel(modelName);
        ensureConversationExists(userId, sessionId, "新对话", effectiveModelName);
        List<Message> history = getConversation(userId, sessionId);
        if (history == null || history.isEmpty()) {
            return "无对话内容";
        }

        StringBuilder conversationText = new StringBuilder();
        for (Message message : history) {
            conversationText.append(message.getRole()).append(": ").append(message.getContent()).append("\n");
            if (conversationText.length() > 12000) {
                break;
            }
        }

        String prompt = "请总结以下对话的核心内容，提取关键信息，用中文输出：\n" + conversationText;
        List<Message> messages = List.of(new Message("user", prompt));

        try {
            if ("glm-4.6v-Flash".equalsIgnoreCase(effectiveModelName)) {
                String s = bigModelService.chatOnce(messages, "glm-4.6v-Flash").map(BigModelService.BigModelReply::getContent).block();
                return s == null || s.isBlank() ? "总结生成失败" : s.trim();
            }
            String s = siliconFlowService.chatOnce(messages, effectiveModelName).block();
            return s == null || s.isBlank() ? "总结生成失败" : s.trim();
        } catch (Exception e) {
            return "总结生成失败";
        }
    }

    public void syncToDatabase(Long userId, String sessionId) {
        List<Message> memoryMessages = getConversation(userId, sessionId);
        for (Message message : memoryMessages) {
            databaseConversationService.saveMessage(userId, sessionId, message);
        }
    }

    public void syncToRedis(Long userId, String sessionId) {
    }

    public void deleteAllUserConversations(Long userId) {
        redisConversationService.deleteAllUserConversations(userId);
        databaseConversationService.deleteAllUserConversations(userId);
    }
}
