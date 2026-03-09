package com.syh.chat.controller;

import com.syh.chat.service.QuestionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final QuestionService questionService;

    public StatsController(QuestionService questionService) {
        this.questionService = questionService;
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    @GetMapping("/overview")
    public ResponseEntity<Map<String, Object>> overview(HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return ResponseEntity.ok(questionService.statsOverview(userId));
    }

    @GetMapping("/wrongbook")
    public ResponseEntity<List<Map<String, Object>>> wrongbook(
            @RequestParam(value = "groupId", required = false) Long groupId,
            @RequestParam(value = "ungrouped", required = false, defaultValue = "false") boolean ungrouped,
            HttpServletRequest httpRequest
    ) {
        Long userId = getUserId(httpRequest);
        return ResponseEntity.ok(questionService.wrongBook(userId, groupId, ungrouped));
    }
}

