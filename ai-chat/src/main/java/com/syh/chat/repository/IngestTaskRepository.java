package com.syh.chat.repository;

import com.syh.chat.entity.IngestTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IngestTaskRepository extends JpaRepository<IngestTask, String> {
    Optional<IngestTask> findByIdAndUserId(String id, Long userId);
}
