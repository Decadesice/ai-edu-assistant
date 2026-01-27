package com.syh.chat.controller;

import com.syh.chat.dto.ConversationRequest;
import com.syh.chat.dto.ConversationSummaryResponse;
import com.syh.chat.dto.MessageFragmentRequest;
import com.syh.chat.model.Message;
import com.syh.chat.service.UnifiedConversationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/unified")
public class UnifiedChatController {

    private final UnifiedConversationService unifiedConversationService;

    public UnifiedChatController(UnifiedConversationService unifiedConversationService) {
        this.unifiedConversationService = unifiedConversationService;
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public Flux<String> chatStream(
            @RequestBody ChatRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        String sessionId = request.getSessionId();

        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = unifiedConversationService.createNewConversation(userId, "新对话", request.getModel());
        }

        return unifiedConversationService.chatWithStream(userId, sessionId, request.getMessage(), request.getModel(),
                request.getImage());
    }

    @PostMapping("/conversation/new")
    public ResponseEntity<String> createNewConversation(
            @RequestBody ConversationRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        String sessionId = unifiedConversationService.createNewConversation(
                userId,
                request.getTitle() != null ? request.getTitle() : "新对话",
                request.getModelName());
        return ResponseEntity.ok(sessionId);
    }

    @PostMapping("/conversation/continue")
    public ResponseEntity<String> continueConversation(
            @RequestBody ConversationRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        String sessionId = request.getSessionId();

        if (sessionId != null && !sessionId.isEmpty()) {
            unifiedConversationService.continueFromHistory(userId, sessionId);
            return ResponseEntity.ok(sessionId);
        }

        return ResponseEntity.badRequest().body("Session ID is required");
    }

    @PostMapping("/conversation/fragment")
    public ResponseEntity<String> continueFromFragment(
            @RequestBody MessageFragmentRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        unifiedConversationService.continueFromFragment(
                userId,
                request.getSessionId(),
                request.getStartOrder(),
                request.getEndOrder());
        return ResponseEntity.ok("已从指定片段继续对话");
    }

    @GetMapping("/conversation/list")
    public ResponseEntity<List<String>> getUserConversations(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<String> conversations = unifiedConversationService.getUserConversations(userId);
        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/conversation/list/detail")
    public ResponseEntity<List<ConversationSummaryResponse>> getUserConversationsDetail(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<ConversationSummaryResponse> conversations = unifiedConversationService.getUserConversationSummaries(userId);
        return ResponseEntity.ok(conversations);
    }

    @GetMapping("/conversation/{sessionId}")
    public ResponseEntity<List<Message>> getConversation(
            @PathVariable String sessionId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        List<Message> conversation = unifiedConversationService.getConversation(userId, sessionId);
        return ResponseEntity.ok(conversation);
    }

    @GetMapping("/conversation/{sessionId}/fragment")
    public ResponseEntity<List<Message>> getMessageFragment(
            @PathVariable String sessionId,
            @RequestParam int startOrder,
            @RequestParam int endOrder,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        List<Message> fragment = unifiedConversationService.getMessageFragment(
                userId,
                sessionId,
                startOrder,
                endOrder);
        return ResponseEntity.ok(fragment);
    }

    @GetMapping("/conversation/{sessionId}/summary")
    public ResponseEntity<String> summarizeConversation(
            @PathVariable String sessionId,
            @RequestParam String model,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        String summary = unifiedConversationService.summarizeConversation(userId, sessionId, model);
        return ResponseEntity.ok(summary);
    }

    @DeleteMapping("/conversation/{sessionId}")
    public ResponseEntity<String> deleteConversation(
            @PathVariable String sessionId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        unifiedConversationService.deleteConversation(userId, sessionId);
        return ResponseEntity.ok("对话已删除");
    }

    @DeleteMapping("/conversation/{sessionId}/message/{messageOrder}")
    public ResponseEntity<String> deleteMessage(
            @PathVariable String sessionId,
            @PathVariable int messageOrder,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        unifiedConversationService.deleteMessage(userId, sessionId, messageOrder);
        return ResponseEntity.ok("消息已删除");
    }

    @DeleteMapping("/conversation/all")
    public ResponseEntity<String> deleteAllConversations(HttpServletRequest request) {
        Long userId = getUserId(request);
        unifiedConversationService.deleteAllUserConversations(userId);
        return ResponseEntity.ok("所有对话已删除");
    }

    @PostMapping("/conversation/{sessionId}/sync/database")
    public ResponseEntity<String> syncToDatabase(
            @PathVariable String sessionId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        unifiedConversationService.syncToDatabase(userId, sessionId);
        return ResponseEntity.ok("已同步到数据库");
    }

    @PostMapping("/conversation/{sessionId}/sync/redis")
    public ResponseEntity<String> syncToRedis(
            @PathVariable String sessionId,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        unifiedConversationService.syncToRedis(userId, sessionId);
        return ResponseEntity.ok("已同步到Redis");
    }

    public static class ChatRequest {
        private String sessionId;
        private String message;
        private String model;
        private String image;

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getImage() {
            return image;
        }

        public void setImage(String image) {
            this.image = image;
        }
    }
}

