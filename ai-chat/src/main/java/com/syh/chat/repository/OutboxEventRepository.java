package com.syh.chat.repository;

import com.syh.chat.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findTop20ByStatusOrderByCreatedAtAsc(String status);
}
