package com.syh.chat.repository;

import com.syh.chat.entity.KnowledgeSegment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface KnowledgeSegmentRepository extends JpaRepository<KnowledgeSegment, Long> {
    List<KnowledgeSegment> findByUserIdAndDocumentIdOrderBySegmentIndexAsc(Long userId, Long documentId);
    long countByUserIdAndDocumentId(Long userId, Long documentId);
    void deleteByUserIdAndDocumentId(Long userId, Long documentId);
    KnowledgeSegment findTop1ByUserIdAndContentStartingWith(Long userId, String prefix);
}

