package com.syh.chat.repository;

import com.syh.chat.entity.QuestionAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionAttemptRepository extends JpaRepository<QuestionAttempt, Long> {
    List<QuestionAttempt> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<QuestionAttempt> findByUserIdAndQuestionIdOrderByCreatedAtDesc(Long userId, Long questionId);
}


