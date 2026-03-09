package com.syh.chat.repository;

import com.syh.chat.entity.IngestTaskTransition;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestTaskTransitionRepository extends JpaRepository<IngestTaskTransition, Long> {
}

