package com.syh.chat.repository;

import com.syh.chat.entity.KnowledgeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
    List<KnowledgeDocument> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<KnowledgeDocument> findByIdAndUserId(Long id, Long userId);
}


