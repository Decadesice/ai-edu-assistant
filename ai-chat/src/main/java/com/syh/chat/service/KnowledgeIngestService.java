package com.syh.chat.service;

import com.syh.chat.entity.KnowledgeDocument;
import com.syh.chat.entity.KnowledgeSegment;
import com.syh.chat.repository.KnowledgeDocumentRepository;
import com.syh.chat.repository.KnowledgeSegmentRepository;
import com.syh.chat.model.Message;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class KnowledgeIngestService {

    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeSegmentRepository segmentRepository;
    private final EmbeddingModel embeddingModel;
    private final ChromaVectorStoreService chromaVectorStoreService;
    private final SiliconFlowService siliconFlowService;
    private final BigModelService bigModelService;
    private final String summaryModelName;
    private final MeterRegistry meterRegistry;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public KnowledgeIngestService(
            KnowledgeDocumentRepository documentRepository,
            KnowledgeSegmentRepository segmentRepository,
            @Lazy EmbeddingModel embeddingModel,
            ChromaVectorStoreService chromaVectorStoreService,
            SiliconFlowService siliconFlowService,
            BigModelService bigModelService,
            @Value("${knowledge.summary-model:Auto}") String summaryModelName,
            MeterRegistry meterRegistry,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.documentRepository = documentRepository;
        this.segmentRepository = segmentRepository;
        this.embeddingModel = embeddingModel;
        this.chromaVectorStoreService = chromaVectorStoreService;
        this.siliconFlowService = siliconFlowService;
        this.bigModelService = bigModelService;
        this.summaryModelName = summaryModelName;
        this.meterRegistry = meterRegistry;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public KnowledgeDocument ingestExistingDocumentFromFile(Long userId, Long documentId, String filePath, BiConsumer<Integer, Integer> progress) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("文件路径为空");
        }
        try {
            byte[] bytes = Files.readAllBytes(Path.of(filePath));
            return ingestExistingDocument(userId, documentId, bytes, progress);
        } catch (Exception e) {
            throw new IllegalArgumentException("读取文件失败");
        }
    }

    @Transactional
    public KnowledgeDocument ingestExistingDocument(Long userId, Long documentId, byte[] pdfBytes, BiConsumer<Integer, Integer> progress) {
        KnowledgeDocument doc = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));

        Timer.Sample sample = Timer.start(meterRegistry);
        String result = "error";
        doc.setStatus("PROCESSING");
        doc.setSegmentCount(0);
        doc.setUpdatedAt(LocalDateTime.now());
        documentRepository.save(doc);
        segmentRepository.deleteByUserIdAndDocumentId(userId, documentId);

        String text = extractPdfText(pdfBytes);
        List<String> chunksRaw = chunkText(text, 900, 120);
        List<String> chunks = new ArrayList<>();
        for (String chunk : chunksRaw) {
            String cleaned = normalizeWhitespace(chunk);
            if (!cleaned.isBlank()) {
                chunks.add(cleaned);
            }
        }
        int total = chunks.size();
        if (progress != null) {
            progress.accept(0, total);
        }

        try {
            int idx = 0;
            for (String cleaned : chunks) {
                String chromaId = buildChromaId(doc.getId(), idx);
                TextSegment segment = TextSegment.from(cleaned);
                Embedding embedding = embeddingModel.embed(segment).content();
                chromaVectorStoreService.upsert(
                        doc.getId(),
                        chromaId,
                        embedding,
                        cleaned,
                        java.util.Map.of(
                                "userId", userId,
                                "documentId", doc.getId(),
                                "segmentIndex", idx
                        )
                );

                KnowledgeSegment ks = new KnowledgeSegment();
                ks.setUserId(userId);
                ks.setDocumentId(doc.getId());
                ks.setSegmentIndex(idx);
                ks.setContent(truncate(cleaned, 2000));
                ks.setChromaId(chromaId);
                ks.setCreatedAt(LocalDateTime.now());
                segmentRepository.save(ks);
                idx++;

                if (progress != null) {
                    progress.accept(idx, total);
                }
            }

            doc.setStatus("READY");
            doc.setSegmentCount(total);
            doc.setUpdatedAt(LocalDateTime.now());
            result = "ok";
            return documentRepository.save(doc);
        } catch (RuntimeException e) {
            Counter.builder("knowledge_ingest_failures_total").register(meterRegistry).increment();
            doc.setStatus("FAILED");
            doc.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(doc);
            RuntimeException mapped = mapIngestRuntimeException(e);
            if (mapped != null) {
                throw mapped;
            }
            throw e;
        } finally {
            sample.stop(Timer.builder("knowledge_ingest_seconds").tag("result", result).register(meterRegistry));
        }
    }

    @Transactional
    public KnowledgeDocument uploadAndIngest(Long userId, MultipartFile file, String titleOverride) {
        Timer.Sample sample = Timer.start(meterRegistry);
        if (file == null || file.isEmpty()) {
            sample.stop(Timer.builder("knowledge_ingest_seconds").tag("result", "invalid").register(meterRegistry));
            throw new IllegalArgumentException("文件为空");
        }
        String originalName = file.getOriginalFilename() == null ? "document.pdf" : file.getOriginalFilename();
        String title = (titleOverride == null || titleOverride.isBlank()) ? originalName : titleOverride.trim();

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setUserId(userId);
        doc.setTitle(title);
        doc.setStatus("PROCESSING");
        doc.setSegmentCount(0);
        doc.setCreatedAt(LocalDateTime.now());
        doc.setUpdatedAt(LocalDateTime.now());
        doc = documentRepository.save(doc);

        String text = extractPdfText(file);
        List<String> chunks = chunkText(text, 900, 120);

        String result = "error";
        try {
            int idx = 0;
            for (String chunk : chunks) {
                String cleaned = normalizeWhitespace(chunk);
                if (cleaned.isBlank()) {
                    continue;
                }
                String chromaId = buildChromaId(doc.getId(), idx);
                TextSegment segment = TextSegment.from(cleaned);
                Embedding embedding = embeddingModel.embed(segment).content();
                chromaVectorStoreService.upsert(
                        doc.getId(),
                        chromaId,
                        embedding,
                        cleaned,
                        java.util.Map.of(
                                "userId", userId,
                                "documentId", doc.getId(),
                                "segmentIndex", idx
                        )
                );

                KnowledgeSegment ks = new KnowledgeSegment();
                ks.setUserId(userId);
                ks.setDocumentId(doc.getId());
                ks.setSegmentIndex(idx);
                ks.setContent(truncate(cleaned, 2000));
                ks.setChromaId(chromaId);
                ks.setCreatedAt(LocalDateTime.now());
                segmentRepository.save(ks);

                idx++;
            }

            doc.setStatus("READY");
            doc.setSegmentCount(idx);
            doc.setUpdatedAt(LocalDateTime.now());
            result = "ok";
            return documentRepository.save(doc);
        } catch (RuntimeException e) {
            Counter.builder("knowledge_ingest_failures_total").register(meterRegistry).increment();
            doc.setStatus("FAILED");
            doc.setUpdatedAt(LocalDateTime.now());
            documentRepository.save(doc);
            RuntimeException mapped = mapIngestRuntimeException(e);
            if (mapped != null) {
                throw mapped;
            }
            throw e;
        } finally {
            sample.stop(Timer.builder("knowledge_ingest_seconds").tag("result", result).register(meterRegistry));
        }

    }

    private RuntimeException mapIngestRuntimeException(RuntimeException e) {
        String msg = (e == null || e.getMessage() == null) ? "" : e.getMessage().trim();
        String lower = msg.toLowerCase();
        if (lower.contains("siliconflow")) {
            String detail = extractSiliconFlowDetail(msg);
            if (lower.contains("failed to resolve") || lower.contains("unknownhost") || lower.contains("unknown host") || lower.contains("name or service not known")) {
                return new IllegalArgumentException("向量化失败：无法解析 api.siliconflow.cn（DNS）。如果通过 Docker 启动，请为 backend 容器配置 dns，或检查宿主机网络/DNS。" + detail);
            }
            if (lower.contains("api key") || lower.contains("未配置") || lower.contains("not configured")) {
                return new IllegalArgumentException("向量化失败：SiliconFlow API Key 未配置，请设置环境变量 SILICONFLOW_API_KEY。" + detail);
            }
            if (lower.contains("http 401") || lower.contains("http 403")) {
                return new IllegalArgumentException("向量化失败：SiliconFlow 鉴权失败，请检查 SILICONFLOW_API_KEY 是否正确。" + detail);
            }
            if (lower.contains("http 429")) {
                return new IllegalArgumentException("向量化失败：SiliconFlow 请求过于频繁（HTTP 429）。请稍后重试。" + detail);
            }
            if (lower.contains("http 400")) {
                return new IllegalArgumentException("向量化失败：SiliconFlow Embeddings 请求参数错误（HTTP 400）。" + detail);
            }
            return new IllegalArgumentException("向量化失败：SiliconFlow Embeddings 调用失败。" + detail);
        }
        if (msg.contains(":8000") || lower.contains("chroma")) {
            return new IllegalArgumentException("无法连接 Chroma(8000)。请确认 chroma-db 容器已启动且端口已映射到本机 8000");
        }
        return null;
    }

    private String extractSiliconFlowDetail(String msg) {
        if (msg == null || msg.isBlank()) {
            return "";
        }
        String s = msg.replaceAll("\\s+", " ").trim();
        int idx = s.indexOf("HTTP ");
        if (idx >= 0) {
            s = s.substring(idx).trim();
        }
        if (s.length() > 300) {
            s = s.substring(0, 300) + "...";
        }
        return s.isBlank() ? "" : " 详情: " + s;
    }

    public List<KnowledgeDocument> listDocuments(Long userId) {
        return documentRepository.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    @Transactional
    public void deleteDocument(Long userId, Long documentId) {
        KnowledgeDocument doc = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));
        segmentRepository.deleteByUserIdAndDocumentId(userId, documentId);
        documentRepository.delete(Objects.requireNonNull(doc));
    }

    @Transactional
    public String getOrGenerateSummary(Long userId, Long documentId) {
        KnowledgeDocument doc = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));

        if (doc.getSummary() != null && !doc.getSummary().isBlank() && !"摘要生成失败，请稍后重试。".equals(doc.getSummary().trim())) {
            return doc.getSummary();
        }

        List<KnowledgeSegment> segments = segmentRepository.findByUserIdAndDocumentIdOrderBySegmentIndexAsc(userId, documentId);
        StringBuilder fullText = new StringBuilder();
        for (KnowledgeSegment seg : segments) {
            fullText.append(seg.getContent()).append("\n");
            if (fullText.length() > 10000) {
                break;
            }
        }

        if (fullText.isEmpty()) {
            return "文档内容为空，无法生成摘要。";
        }

        String prompt = "你是文档摘要助手。请为以下文档生成一份简明的中文摘要（500字以内），用自然段输出：\n\n" + fullText.toString();
        List<Message> messages = List.of(new Message("user", prompt));

        String summary = generateSummaryWithFallback(messages, summaryModelName);
        summary = summary == null ? "" : summary.trim();
        if (summary.isBlank()) {
            summary = "摘要生成失败，请稍后重试。";
        }
        doc.setSummary(summary);
        documentRepository.save(doc);
        return summary;
    }

    public Map<String, Object> getSummaryTaskState(Long userId, Long documentId) {
        KnowledgeDocument doc = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));

        if (doc.getSummary() != null && !doc.getSummary().isBlank() && !"摘要生成失败，请稍后重试。".equals(doc.getSummary().trim())) {
            return Map.of("status", "DONE", "summary", doc.getSummary());
        }

        String key = buildSummaryTaskKey(userId, documentId);
        String json = null;
        try {
            json = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            // Ignore redis connection/serialization errors
        }

        if (json != null) {
            try {
                Map<?, ?> m = objectMapper.readValue(json, Map.class);
                Object status = m.get("status");
                Object error = m.get("error");
                if (status != null) {
                    return Map.of(
                            "status", String.valueOf(status),
                            "summary", doc.getSummary() == null ? "" : doc.getSummary(),
                            "error", error == null ? "" : String.valueOf(error)
                    );
                }
            } catch (Exception e) {
                // Invalid JSON in redis, ignore
                redisTemplate.delete(key);
            }
        }

        return Map.of("status", "IDLE", "summary", doc.getSummary() == null ? "" : doc.getSummary());
    }

    public Map<String, Object> startSummaryTask(Long userId, Long documentId) {
        KnowledgeDocument doc = documentRepository.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));
        if (doc.getSummary() != null && !doc.getSummary().isBlank() && !"摘要生成失败，请稍后重试。".equals(doc.getSummary().trim())) {
            return Map.of("status", "DONE", "summary", doc.getSummary());
        }

        String key = buildSummaryTaskKey(userId, documentId);
        Map<String, Object> init = Map.of(
                "status", "RUNNING",
                "updatedAt", System.currentTimeMillis()
        );
        String initJson;
        try {
            initJson = objectMapper.writeValueAsString(init);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON serialization error", e);
        }

        Boolean acquired;
        try {
            acquired = redisTemplate.opsForValue().setIfAbsent(key, initJson, Duration.ofHours(1));
        } catch (Exception e) {
            // If key exists but holds wrong type (e.g. from previous version), delete and retry
            redisTemplate.delete(key);
            acquired = redisTemplate.opsForValue().setIfAbsent(key, initJson, Duration.ofHours(1));
        }

        if (Boolean.FALSE.equals(acquired)) {
            return getSummaryTaskState(userId, documentId);
        }

        Mono.fromRunnable(() -> {
                    try {
                        String s = getOrGenerateSummary(userId, documentId);
                        Map<String, Object> doneMap = Map.of(
                                "status", "DONE",
                                "updatedAt", System.currentTimeMillis(),
                                "summaryLen", s == null ? 0 : s.length()
                        );
                        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(doneMap), Duration.ofHours(24));
                    } catch (Exception e) {
                        String msg = e.getMessage() == null ? "摘要生成失败" : e.getMessage();
                        try {
                            Map<String, Object> errorMap = Map.of(
                                    "status", "ERROR",
                                    "updatedAt", System.currentTimeMillis(),
                                    "error", msg
                            );
                            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(errorMap), Duration.ofHours(1));
                        } catch (Exception ignored) {}
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();

        return Map.of("status", "RUNNING");
    }

    private String buildSummaryTaskKey(Long userId, Long documentId) {
        return "knowledge:summary:task:" + userId + ":" + documentId;
    }

    private String generateSummaryWithFallback(List<Message> messages, String rawModel) {
        List<ModelPolicy.ModelCandidate> candidates;
        if (rawModel == null || rawModel.isBlank() || ModelPolicy.isAuto(rawModel)) {
            candidates = ModelPolicy.autoNonVisionOnceCandidates();
        } else if (ModelPolicy.isAdvanced(rawModel)) {
            candidates = List.of(new ModelPolicy.ModelCandidate("glm-4.7-flash", ModelPolicy.Provider.BIGMODEL, false, true, 1));
        } else {
            candidates = List.of(ModelPolicy.normalizeExplicitModel(rawModel));
        }

        for (int i = 0; i < candidates.size(); i++) {
            ModelPolicy.ModelCandidate c = candidates.get(i);
            try {
                if (c.provider() == ModelPolicy.Provider.SILICONFLOW) {
                    String s = siliconFlowService.chatOnce(messages, c.modelName()).block();
                    if (s != null && !s.isBlank()) return s;
                } else {
                    String s = bigModelService.chatOnce(messages, c.modelName()).map(BigModelService.BigModelReply::getContent).block();
                    if (s != null && !s.isBlank()) return s;
                }
            } catch (Exception ignored) {
            }

            boolean canFallback = (rawModel == null || rawModel.isBlank() || ModelPolicy.isAuto(rawModel)) && i + 1 < candidates.size();
            if (!canFallback) {
                break;
            }
        }
        return "";
    }

    private String extractPdfText(MultipartFile file) {
        try (PDDocument pdf = PDDocument.load(file.getBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(pdf);
            return new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("PDF解析失败");
        }
    }

    private String extractPdfText(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("PDF解析失败");
        }
        try (PDDocument pdf = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(pdf);
            return new String(text.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("PDF解析失败");
        }
    }

    private List<String> chunkText(String text, int maxChars, int overlap) {
        String normalized = normalizeWhitespace(text);
        List<String> chunks = new ArrayList<>();
        if (normalized.isBlank()) {
            return chunks;
        }
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + maxChars);
            String chunk = normalized.substring(start, end);
            chunks.add(chunk);
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(0, end - overlap);
        }
        return chunks;
    }

    private String normalizeWhitespace(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ").trim();
    }

    private String buildChromaId(Long documentId, int index) {
        return "doc-" + documentId + "-seg-" + index + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }
}
