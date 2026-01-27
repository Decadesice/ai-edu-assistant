package com.syh.chat.service;

import com.syh.chat.dto.RagContextResponse;
import com.syh.chat.entity.KnowledgeSegment;
import com.syh.chat.repository.KnowledgeSegmentRepository;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
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

    public RagRetrieveService(
            @Lazy EmbeddingModel embeddingModel,
            ChromaVectorStoreService chromaVectorStoreService,
            KnowledgeSegmentRepository segmentRepository
    ) {
        this.embeddingModel = embeddingModel;
        this.chromaVectorStoreService = chromaVectorStoreService;
        this.segmentRepository = segmentRepository;
    }

    public RagContextResponse retrieveContext(Long userId, Long documentId, String query, int topK) {
        if (documentId == null || query == null || query.isBlank()) {
            return new RagContextResponse(List.of());
        }
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        List<String> docs;
        try {
            docs = chromaVectorStoreService.queryDocuments(documentId, queryEmbedding, topK);
        } catch (Exception e) {
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
        return new RagContextResponse(snippets);
    }

    private KnowledgeSegment findSegmentByContentPrefix(Long userId, String content) {
        String prefix = content.length() > 80 ? content.substring(0, 80) : content;
        return segmentRepository.findTop1ByUserIdAndContentStartingWith(userId, prefix);
    }
}

