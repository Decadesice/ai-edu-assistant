package com.syh.chat.controller;

import com.syh.chat.dto.ConversationRequest;
import com.syh.chat.dto.MessageFragmentRequest;
import com.syh.chat.model.Message;
import com.syh.chat.service.UserConversationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversation")
public class ConversationController {
    
    private final UserConversationService userConversationService;
    
    public ConversationController(UserConversationService userConversationService) {
        this.userConversationService = userConversationService;
    }
    
    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
    
    @PostMapping("/new")
    public ResponseEntity<String> createNewConversation(HttpServletRequest request) {
        Long userId = getUserId(request);
        String sessionId = userConversationService.createNewConversation(userId);
        return ResponseEntity.ok(sessionId);
    }
    
    @GetMapping("/list")
    public ResponseEntity<List<String>> getUserConversations(HttpServletRequest request) {
        Long userId = getUserId(request);
        List<String> sessionIds = userConversationService.getUserSessionIds(userId);
        return ResponseEntity.ok(sessionIds);
    }
    
    @GetMapping("/{sessionId}")
    public ResponseEntity<List<Message>> getConversation(
            @PathVariable String sessionId,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        List<Message> conversation = userConversationService.getConversation(userId, sessionId);
        return ResponseEntity.ok(conversation);
    }
    
    @PostMapping("/continue")
    public ResponseEntity<String> continueConversation(
            @RequestBody ConversationRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = userConversationService.createNewConversation(userId);
        }
        return ResponseEntity.ok(sessionId);
    }
    
    @PostMapping("/fragment")
    public ResponseEntity<List<Message>> getMessageFragment(
            @RequestBody MessageFragmentRequest request,
            HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        List<Message> fragment = userConversationService.getMessageFragment(
                userId,
                request.getSessionId(),
                request.getStartOrder(),
                request.getEndOrder()
        );
        return ResponseEntity.ok(fragment);
    }
    
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<String> deleteConversation(
            @PathVariable String sessionId,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        userConversationService.deleteConversation(userId, sessionId);
        return ResponseEntity.ok("对话已删除");
    }
    
    @DeleteMapping("/{sessionId}/message/{messageOrder}")
    public ResponseEntity<String> deleteMessage(
            @PathVariable String sessionId,
            @PathVariable int messageOrder,
            HttpServletRequest request) {
        Long userId = getUserId(request);
        userConversationService.deleteMessage(userId, sessionId, messageOrder);
        return ResponseEntity.ok("消息已删除");
    }
    
    @DeleteMapping("/all")
    public ResponseEntity<String> deleteAllConversations(HttpServletRequest request) {
        Long userId = getUserId(request);
        userConversationService.deleteAllUserConversations(userId);
        return ResponseEntity.ok("所有对话已删除");
    }
}

