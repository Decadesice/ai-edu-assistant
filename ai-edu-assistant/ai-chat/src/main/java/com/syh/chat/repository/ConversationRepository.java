package com.syh.chat.repository;

import com.syh.chat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {
    
    List<Conversation> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    Optional<Conversation> findBySessionId(String sessionId);
    
    List<Conversation> findByUserIdAndIsActiveOrderByCreatedAtDesc(Long userId, Boolean isActive);
    
    void deleteBySessionId(String sessionId);
}

