package com.syh.chat.repository;

import com.syh.chat.entity.GeneratedQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GeneratedQuestionRepository extends JpaRepository<GeneratedQuestion, Long> {
    List<GeneratedQuestion> findTop50ByUserIdOrderByCreatedAtDesc(Long userId);
    List<GeneratedQuestion> findTop50ByUserIdAndDocumentIdOrderByCreatedAtDesc(Long userId, Long documentId);
    Optional<GeneratedQuestion> findByIdAndUserId(Long id, Long userId);
}


