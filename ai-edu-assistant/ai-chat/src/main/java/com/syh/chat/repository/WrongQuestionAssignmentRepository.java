package com.syh.chat.repository;

import com.syh.chat.entity.WrongQuestionAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WrongQuestionAssignmentRepository extends JpaRepository<WrongQuestionAssignment, Long> {
    Optional<WrongQuestionAssignment> findByUserIdAndQuestionId(Long userId, Long questionId);
    List<WrongQuestionAssignment> findByUserIdAndGroupId(Long userId, Long groupId);
    List<WrongQuestionAssignment> findByUserIdAndGroupIdIsNull(Long userId);
    void deleteByUserIdAndGroupId(Long userId, Long groupId);
}


