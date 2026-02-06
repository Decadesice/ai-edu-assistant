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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

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

    public KnowledgeIngestService(
            KnowledgeDocumentRepository documentRepository,
            KnowledgeSegmentRepository segmentRepository,
            @Lazy EmbeddingModel embeddingModel,
            ChromaVectorStoreService chromaVectorStoreService,
            SiliconFlowService siliconFlowService,
            BigModelService bigModelService,
            @Value("${knowledge.summary-model:THUDM/GLM-4.1V-9B-Thinking}") String summaryModelName,
            MeterRegistry meterRegistry
    ) {
        this.documentRepository = documentRepository;
        this.segmentRepository = segmentRepository;
        this.embeddingModel = embeddingModel;
        this.chromaVectorStoreService = chromaVectorStoreService;
        this.siliconFlowService = siliconFlowService;
        this.bigModelService = bigModelService;
        this.summaryModelName = summaryModelName;
        this.meterRegistry = meterRegistry;
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

            String msg = e.getMessage() == null ? "" : e.getMessage();
            String lower = msg.toLowerCase();
            if (lower.contains("siliconflow")) {
                throw new IllegalArgumentException("向量化失败：SiliconFlow Embeddings 调用异常。请检查 SiliconFlow_Api_Key 与网络连通性。");
            }
            if (msg.contains(":8000") || msg.toLowerCase().contains("chroma")) {
                throw new IllegalArgumentException("无法连接 Chroma(8000)。请确认 chroma-db 容器已启动且端口已映射到本机 8000");
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

            String msg = e.getMessage() == null ? "" : e.getMessage();
            String lower = msg.toLowerCase();
            if (lower.contains("siliconflow")) {
                throw new IllegalArgumentException("向量化失败：SiliconFlow Embeddings 调用异常。请检查 SiliconFlow_Api_Key 与网络连通性。");
            }
            if (msg.contains(":8000") || msg.toLowerCase().contains("chroma")) {
                throw new IllegalArgumentException("无法连接 Chroma(8000)。请确认 chroma-db 容器已启动且端口已映射到本机 8000");
            }
            throw e;
        } finally {
            sample.stop(Timer.builder("knowledge_ingest_seconds").tag("result", result).register(meterRegistry));
        }

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

        String summary = "";
        try {
            summary = siliconFlowService.chatOnce(messages, summaryModelName).block();
        } catch (Exception ignored) {
        }
        if (summary == null || summary.isBlank()) {
            try {
                summary = bigModelService.chatOnce(messages, "glm-4.6v-Flash").map(BigModelService.BigModelReply::getContent).block();
            } catch (Exception ignored) {
            }
        }
        summary = summary == null ? "" : summary.trim();
        if (summary.isBlank()) {
            summary = "摘要生成失败，请稍后重试。";
        }
        doc.setSummary(summary);
        documentRepository.save(doc);
        return summary;
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
