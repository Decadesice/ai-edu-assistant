package com.syh.chat.repository;

import com.syh.chat.entity.IngestTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface IngestTaskRepository extends JpaRepository<IngestTask, String> {
    Optional<IngestTask> findByIdAndUserId(String id, Long userId);

    @Modifying
    @Query("""
            update IngestTask t
            set t.status = :toStatus, t.updatedAt = :now
            where t.id = :taskId
              and t.status in (:fromStatuses)
              and (t.nextRetryAt is null or t.nextRetryAt <= :now)
            """)
    int transitionIfEligible(
            @Param("taskId") String taskId,
            @Param("fromStatuses") java.util.Collection<String> fromStatuses,
            @Param("toStatus") String toStatus,
            @Param("now") LocalDateTime now
    );
}
