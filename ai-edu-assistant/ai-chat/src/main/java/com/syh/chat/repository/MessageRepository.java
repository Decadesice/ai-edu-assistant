package com.syh.chat.repository;

import com.syh.chat.entity.MessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {
    
    List<MessageEntity> findByConversationIdOrderByMessageOrderAsc(Long conversationId);
    
    List<MessageEntity> findByConversationIdAndMessageOrderBetweenOrderByMessageOrderAsc(
        Long conversationId, Integer startOrder, Integer endOrder);
    
    void deleteByConversationId(Long conversationId);
    
    void deleteByConversationIdAndMessageOrder(Long conversationId, Integer messageOrder);
}

