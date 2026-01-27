package com.syh.chat.controller;

import com.syh.chat.entity.WrongQuestionGroup;
import com.syh.chat.service.QuestionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/wrongbook")
public class WrongBookController {

    private final QuestionService questionService;

    public WrongBookController(QuestionService questionService) {
        this.questionService = questionService;
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    @GetMapping("/groups")
    public ResponseEntity<List<WrongQuestionGroup>> listGroups(HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return ResponseEntity.ok(questionService.listWrongQuestionGroups(userId));
    }

    @PostMapping("/groups")
    public ResponseEntity<WrongQuestionGroup> createGroup(@RequestBody Map<String, Object> body, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        String name = body == null ? null : String.valueOf(body.getOrDefault("name", ""));
        return ResponseEntity.ok(questionService.createWrongQuestionGroup(userId, name));
    }

    @PutMapping("/groups/{id}")
    public ResponseEntity<WrongQuestionGroup> renameGroup(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest
    ) {
        Long userId = getUserId(httpRequest);
        String name = body == null ? null : String.valueOf(body.getOrDefault("name", ""));
        return ResponseEntity.ok(questionService.renameWrongQuestionGroup(userId, id, name));
    }

    @DeleteMapping("/groups/{id}")
    public ResponseEntity<Map<String, Object>> deleteGroup(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        questionService.deleteWrongQuestionGroup(userId, id);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PutMapping("/questions/{questionId}/group")
    public ResponseEntity<Map<String, Object>> assignGroup(
            @PathVariable("questionId") Long questionId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest
    ) {
        Long userId = getUserId(httpRequest);
        Long groupId = null;
        if (body != null && body.containsKey("groupId") && body.get("groupId") != null) {
            Object v = body.get("groupId");
            if (v instanceof Number n) {
                groupId = n.longValue();
            } else {
                String s = String.valueOf(v).trim();
                if (!s.isEmpty() && !"null".equalsIgnoreCase(s)) {
                    groupId = Long.parseLong(s);
                }
            }
        }
        questionService.assignWrongQuestionGroup(userId, questionId, groupId);
        return ResponseEntity.ok(Map.of("ok", true));
    }
}


