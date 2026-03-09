package com.syh.chat.service;

import com.syh.chat.model.Message;
import com.syh.chat.dto.ConversationSummaryResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class UnifiedConversationService {

    private final RedisConversationService redisConversationService;
    private final DatabaseConversationService databaseConversationService;
    private final OllamaService ollamaService;

    public UnifiedConversationService(
            RedisConversationService redisConversationService,
            DatabaseConversationService databaseConversationService,
            OllamaService ollamaService) {
        this.redisConversationService = redisConversationService;
        this.databaseConversationService = databaseConversationService;
        this.ollamaService = ollamaService;
    }

    private void ensureConversationExists(Long userId, String sessionId, String title, String modelName) {
        databaseConversationService.ensureConversation(userId, sessionId, title, modelName);
        redisConversationService.addSessionToUser(userId, sessionId);
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
        String storedModel = ModelPolicy.normalizeForStorage(modelName, false);
        String sessionId = databaseConversationService.createConversation(userId, title, storedModel);
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
        boolean hasImageInput = image != null && !image.isBlank();
        String storedModel = ModelPolicy.normalizeForStorage(modelName, hasImageInput);
        ensureConversationExists(userId, sessionId, "新对话", storedModel);
        Message message = new Message("user", userMessage);
        if (hasImageInput) {
            message.setImages(java.util.List.of(image));
        }
        addMessage(userId, sessionId, message);

        List<Message> conversation = getConversation(userId, sessionId);
        boolean hasAnyImage = hasAnyImage(conversation);
        List<ModelPolicy.ModelCandidate> candidates = ModelPolicy.candidatesForChat(modelName, hasAnyImage);
        return chatWithModelFallback(userId, sessionId, userMessage, modelName, image, conversation, candidates);
    }

    private boolean hasAnyImage(List<Message> conversation) {
        if (conversation == null || conversation.isEmpty()) return false;
        for (Message m : conversation) {
            if (m != null && m.getImages() != null && !m.getImages().isEmpty()) {
                String img = m.getImages().get(0);
                if (img != null && !img.isBlank()) return true;
            }
        }
        return false;
    }

    private Flux<ChatDelta> streamByProvider(List<Message> conversation, ModelPolicy.ModelCandidate candidate) {
        return ollamaService.chatStream(conversation, candidate.modelName());
    }

    private String toModeLabel(String raw) {
        if (ModelPolicy.isSmall(raw)) return "Small";
        if (ModelPolicy.isMedium(raw)) return "Medium";
        if (ModelPolicy.isLarge(raw)) return "Large";
        if (raw == null || raw.isBlank()) return "Small";
        return "Explicit";
    }

    private boolean isOverloadedError(Throwable e) {
        if (e == null) return false;
        if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException w) {
            int code = w.getStatusCode().value();
            return code == 429 || code == 503;
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("HTTP 429") || msg.contains("429") || msg.toLowerCase().contains("too many requests");
    }

    private boolean isDnsResolveError(Throwable e) {
        if (e == null) return false;
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < 6) {
            if (cur instanceof java.net.UnknownHostException) {
                return true;
            }
            cur = cur.getCause();
            depth++;
        }
        String msg = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return msg.contains("failed to resolve") || msg.contains("unknownhost") || msg.contains("unknown host") || msg.contains("name or service not known");
    }

    private boolean isHttp400(Throwable e) {
        if (e == null) return false;
        if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException w) {
            return w.getStatusCode().value() == 400;
        }
        String msg = e.getMessage() == null ? "" : e.getMessage();
        return msg.contains("HTTP 400") || msg.contains(" 400 ");
    }

    private String dnsFriendlyMessage(Throwable e) {
        String raw = e == null || e.getMessage() == null ? "" : e.getMessage();
        if (raw.contains("localhost:11434") || raw.contains("127.0.0.1:11434")) {
            return "网络异常：无法连接本地 Ollama 服务（localhost:11434）。请确认 Ollama 服务已启动。";
        }
        return "网络异常：无法连接 Ollama 服务。请确认 Ollama 服务已启动且地址配置正确。";
    }

    private Flux<String> chatWithModelFallback(
            Long userId,
            String sessionId,
            String userMessage,
            String rawModel,
            String image,
            List<Message> conversation,
            List<ModelPolicy.ModelCandidate> candidates
    ) {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        int chunkSize = 36;

        return Flux.defer(() -> tryCandidateStream(
                userId,
                sessionId,
                userMessage,
                rawModel,
                image,
                conversation,
                candidates,
                0,
                mapper,
                chunkSize
        ));
    }

    private Flux<String> tryCandidateStream(
            Long userId,
            String sessionId,
            String userMessage,
            String rawModel,
            String image,
            List<Message> conversation,
            List<ModelPolicy.ModelCandidate> candidates,
            int idx,
            com.fasterxml.jackson.databind.ObjectMapper mapper,
            int chunkSize
    ) {
        if (candidates == null || candidates.isEmpty() || idx >= candidates.size()) {
            com.fasterxml.jackson.databind.node.ObjectNode err = mapper.createObjectNode();
            err.put("type", "error");
            err.put("message", "模型调用失败");
            return Flux.just(err.toString() + "\n", "{\"done\":true}\n");
        }

        ModelPolicy.ModelCandidate candidate = candidates.get(idx);
        StringBuilder fullContent = new StringBuilder();
        AtomicBoolean streamStarted = new AtomicBoolean(false);

        com.fasterxml.jackson.databind.node.ObjectNode meta = mapper.createObjectNode();
        meta.put("type", "meta");
        meta.put("mode", toModeLabel(rawModel));
        meta.put("model", candidate.modelName());
        meta.put("thinking", candidate.thinking());

        Flux<String> metaFlux = Flux.just(meta.toString() + "\n");

        Flux<String> body = streamByProvider(conversation, candidate)
                .concatMap(delta -> {
                    if (delta.isDone()) {
                        return Flux.just("{\"done\":true}\n");
                    }

                    java.util.ArrayList<String> out = new java.util.ArrayList<>();
                    String thinkingDelta = candidate.thinking() ? delta.getReasoningDelta() : null;
                    String contentDelta = delta.getContentDelta();

                    try {
                        if (thinkingDelta != null && !thinkingDelta.isBlank()) {
                            streamStarted.set(true);
                            int i = 0;
                            while (i < thinkingDelta.length()) {
                                String part = thinkingDelta.substring(i, Math.min(thinkingDelta.length(), i + chunkSize));
                                com.fasterxml.jackson.databind.node.ObjectNode t = mapper.createObjectNode();
                                com.fasterxml.jackson.databind.node.ObjectNode m = mapper.createObjectNode();
                                m.put("thinking", part);
                                t.set("message", m);
                                out.add(t.toString() + "\n");
                                i += chunkSize;
                            }
                        }
                        if (contentDelta != null && !contentDelta.isEmpty()) {
                            streamStarted.set(true);
                            fullContent.append(contentDelta);
                            int i = 0;
                            while (i < contentDelta.length()) {
                                String part = contentDelta.substring(i, Math.min(contentDelta.length(), i + chunkSize));
                                com.fasterxml.jackson.databind.node.ObjectNode c = mapper.createObjectNode();
                                com.fasterxml.jackson.databind.node.ObjectNode m = mapper.createObjectNode();
                                m.put("content", part);
                                c.set("message", m);
                                out.add(c.toString() + "\n");
                                i += chunkSize;
                            }
                        }
                    } catch (Exception ignore) {
                        return Flux.empty();
                    }

                    return Flux.fromIterable(out);
                })
                .doOnComplete(() -> {
                    if (!fullContent.isEmpty()) {
                        addMessage(userId, sessionId, new Message("assistant", fullContent.toString()));
                    }
                    generateTitleAsync(userId, sessionId, candidate.modelName(), userMessage, fullContent.toString(), image != null && !image.isBlank());
                })
                .onErrorResume(e -> {
                    boolean canFallback = !streamStarted.get() && idx + 1 < candidates.size();
                    if (canFallback) {
                        return tryCandidateStream(userId, sessionId, userMessage, rawModel, image, conversation, candidates, idx + 1, mapper, chunkSize);
                    }
                    String msg;
                    if (isDnsResolveError(e)) {
                        msg = dnsFriendlyMessage(e);
                    } else {
                        msg = e.getMessage() == null ? "模型调用失败" : e.getMessage();
                    }
                    com.fasterxml.jackson.databind.node.ObjectNode err = mapper.createObjectNode();
                    err.put("type", "error");
                    err.put("message", msg);
                    return Flux.just(err.toString() + "\n", "{\"done\":true}\n");
                });

        return metaFlux.concatWith(body);
    }

    private void generateTitleAsync(Long userId, String sessionId, String modelName, String userText, String assistantText, boolean hasImage) {
        Mono.fromCallable(() -> {
                    if (!databaseConversationService.isConversationTitleDefault(userId, sessionId)) {
                        return null;
                    }
                    try {
                        String title = generateTitleWithOllama(userText, assistantText, hasImage, 12);
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

    private String generateTitleWithOllama(String userText, String assistantText, boolean hasImage, int maxChars) {
        String cleanUser = userText == null ? "" : userText.replaceAll("\\s+", " ").trim();
        String cleanAssistant = assistantText == null ? "" : assistantText.replaceAll("\\s+", " ").trim();

        String prompt = "你是一个标题生成器。请根据以下首轮对话生成一个简短中文标题，严格不超过" + maxChars + "个汉字。"
                + "只输出标题本身，不要解释，不要引号，不要换行，不要以标点结尾。\n"
                + (hasImage ? "补充：用户发送了一张图片。\n" : "")
                + "用户：" + cleanUser + "\n"
                + "助手：" + cleanAssistant + "\n";

        Message m = new Message("user", prompt);
        String result = ollamaService.chatOnce(List.of(m), "qwen3.5:0.8b").block();
        return sanitizeTitle(result, maxChars);
    }

    private String sanitizeTitle(String raw, int maxChars) {
        if (raw == null) {
            return "";
        }
        String title = raw.trim();
        int newlineIdx = title.indexOf('\n');
        if (newlineIdx >= 0) {
            title = title.substring(0, newlineIdx).trim();
        }
        if ((title.startsWith("\"") && title.endsWith("\"")) || (title.startsWith("\u201c") && title.endsWith("\u201d"))) {
            title = title.substring(1, title.length() - 1).trim();
        }
        while (!title.isEmpty()) {
            char last = title.charAt(title.length() - 1);
            if (last == '。' || last == '！' || last == '？' || last == '.' || last == '!' || last == '?' || last == '：' || last == ':' || last == '，' || last == ',' || last == '；' || last == ';') {
                title = title.substring(0, title.length() - 1).trim();
            } else {
                break;
            }
        }
        if (title.length() > maxChars) {
            title = title.substring(0, maxChars);
        }
        return title;
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
        String storedModel = ModelPolicy.normalizeForStorage(modelName, false);
        ensureConversationExists(userId, sessionId, "新对话", storedModel);
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
            ModelPolicy.ModelCandidate lowTier = ModelPolicy.summaryLowTier(hasAnyImage(history));
            String s = ollamaService.chatOnce(messages, lowTier.modelName()).block();
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
