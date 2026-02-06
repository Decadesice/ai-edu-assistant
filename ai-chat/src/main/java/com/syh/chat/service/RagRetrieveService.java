package com.syh.chat.service;

import com.syh.chat.dto.RagContextResponse;
import com.syh.chat.entity.KnowledgeSegment;
import com.syh.chat.repository.KnowledgeSegmentRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RagRetrieveService {

    private final EmbeddingModel embeddingModel;
    private final ChromaVectorStoreService chromaVectorStoreService;
    private final KnowledgeSegmentRepository segmentRepository;
    private final MeterRegistry meterRegistry;

    public RagRetrieveService(
            @Lazy EmbeddingModel embeddingModel,
            ChromaVectorStoreService chromaVectorStoreService,
            KnowledgeSegmentRepository segmentRepository,
            MeterRegistry meterRegistry
    ) {
        this.embeddingModel = embeddingModel;
        this.chromaVectorStoreService = chromaVectorStoreService;
        this.segmentRepository = segmentRepository;
        this.meterRegistry = meterRegistry;
    }

    public RagContextResponse retrieveContext(Long userId, Long documentId, String query, int topK) {
        Timer.Sample overall = Timer.start(meterRegistry);
        if (documentId == null || query == null || query.isBlank()) {
            overall.stop(Timer.builder("rag_retrieve_seconds").tag("result", "invalid").register(meterRegistry));
            return new RagContextResponse(List.of());
        }
        Timer.Sample embeddingSample = Timer.start(meterRegistry);
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        embeddingSample.stop(Timer.builder("rag_embedding_seconds").register(meterRegistry));
        List<String> docs;
        try {
            Timer.Sample chromaSample = Timer.start(meterRegistry);
            docs = chromaVectorStoreService.queryDocuments(documentId, queryEmbedding, topK);
            chromaSample.stop(Timer.builder("rag_chroma_query_seconds").register(meterRegistry));
        } catch (Exception e) {
            Counter.builder("rag_retrieve_failures_total").tag("stage", "chroma_query").register(meterRegistry).increment();
            overall.stop(Timer.builder("rag_retrieve_seconds").tag("result", "error").register(meterRegistry));
            return new RagContextResponse(List.of());
        }

        List<RagContextResponse.RagSnippet> snippets = new ArrayList<>();
        for (String text : docs) {
            String t = Optional.ofNullable(text).orElse("");
            if (t.isBlank()) continue;
            KnowledgeSegment seg = findSegmentByContentPrefix(userId, t);
            if (seg != null) {
                snippets.add(new RagContextResponse.RagSnippet(seg.getDocumentId(), seg.getSegmentIndex(), seg.getContent()));
            } else {
                snippets.add(new RagContextResponse.RagSnippet(documentId, null, t.length() > 600 ? t.substring(0, 600) : t));
            }
        }
        overall.stop(Timer.builder("rag_retrieve_seconds").tag("result", "ok").register(meterRegistry));
        return new RagContextResponse(snippets);
    }

    private KnowledgeSegment findSegmentByContentPrefix(Long userId, String content) {
        String prefix = content.length() > 80 ? content.substring(0, 80) : content;
        return segmentRepository.findTop1ByUserIdAndContentStartingWith(userId, prefix);
    }
}

