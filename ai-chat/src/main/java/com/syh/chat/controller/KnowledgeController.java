package com.syh.chat.controller;

import com.syh.chat.dto.KnowledgeDocumentResponse;
import com.syh.chat.entity.KnowledgeDocument;
import com.syh.chat.service.KnowledgeIngestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
@Tag(name = "知识库", description = "PDF 文档管理与入库接口")
public class KnowledgeController {

    private final KnowledgeIngestService ingestService;

    public KnowledgeController(KnowledgeIngestService ingestService) {
        this.ingestService = ingestService;
    }

    private Long getUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    @GetMapping("/documents")
    @Operation(summary = "查询文档列表", description = "返回当前用户的知识库文档列表。")
    public List<KnowledgeDocumentResponse> listDocuments(HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        return ingestService.listDocuments(userId).stream()
                .map(d -> new KnowledgeDocumentResponse(d.getId(), d.getTitle(), d.getStatus(), d.getSegmentCount(), d.getUpdatedAt()))
                .toList();
    }

    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            HttpServletRequest httpRequest
    ) {
        Long userId = getUserId(httpRequest);
        KnowledgeDocument doc = ingestService.uploadAndIngest(userId, file, title);
        return ResponseEntity.ok(new KnowledgeDocumentResponse(doc.getId(), doc.getTitle(), doc.getStatus(), doc.getSegmentCount(), doc.getUpdatedAt()));
    }

    @DeleteMapping("/documents/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        ingestService.deleteDocument(userId, id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/documents/{id}/summary")
    public ResponseEntity<Map<String, String>> getSummary(@PathVariable("id") Long id, HttpServletRequest httpRequest) {
        Long userId = getUserId(httpRequest);
        String summary = ingestService.getOrGenerateSummary(userId, id);
        return ResponseEntity.ok(Map.of("summary", summary));
    }
}


