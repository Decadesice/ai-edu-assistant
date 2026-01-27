package com.syh.chat.controller;

import com.syh.chat.dto.QuestionAttemptRequest;
import com.syh.chat.dto.QuestionGenerateRequest;
import com.syh.chat.dto.QuestionResponse;
import com.syh.chat.entity.QuestionAttempt;
import com.syh.chat.service.QuestionService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService questionService;

    public QuestionController(QuestionService questionService) {
        this.questionService = questionService;
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    @PostMapping("/generate")
    public ResponseEntity<List<QuestionResponse>> generate(@RequestBody QuestionGenerateRequest request, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        Long docId = request.getDocumentId();
        int count = request.getCount() == null ? 5 : request.getCount();
        List<QuestionResponse> res = questionService.generate(userId, docId, request.getChapterHint(), count, request.getModel(), request.getTypes());
        return ResponseEntity.ok(res);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<QuestionResponse>> recent(
            @RequestParam(value = "documentId", required = false) Long documentId,
            HttpServletRequest httpRequest
    ) {
        Long userId = getUserId(httpRequest);
        return ResponseEntity.ok(questionService.listRecent(userId, documentId));
    }

    @PostMapping("/{id}/attempt")
    public ResponseEntity<Map<String, Object>> attempt(
            @PathVariable("id") Long id,
            @RequestBody QuestionAttemptRequest request,
            HttpServletRequest httpRequest
    ) {
        Long userId = getUserId(httpRequest);
        QuestionAttempt a = questionService.attempt(userId, id, request.getChosen());
        return ResponseEntity.ok(Map.of(
                "correct", Boolean.TRUE.equals(a.getCorrect()),
                "chosen", a.getChosen()
        ));
    }
}

