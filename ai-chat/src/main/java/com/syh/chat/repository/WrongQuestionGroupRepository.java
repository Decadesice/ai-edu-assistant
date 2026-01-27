package com.syh.chat.repository;

import com.syh.chat.entity.WrongQuestionGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WrongQuestionGroupRepository extends JpaRepository<WrongQuestionGroup, Long> {
    List<WrongQuestionGroup> findByUserIdOrderByNameAsc(Long userId);
    Optional<WrongQuestionGroup> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndName(Long userId, String name);
}


