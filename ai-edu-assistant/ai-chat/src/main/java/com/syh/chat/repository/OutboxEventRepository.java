package com.syh.chat.repository;

import com.syh.chat.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    @Query("""
            select e from OutboxEvent e
            where (e.status = 'NEW' and e.nextRetryAt is null)
               or (e.status = 'RETRYING' and e.nextRetryAt is not null and e.nextRetryAt <= :now)
            order by e.createdAt asc
            """)
    List<OutboxEvent> findDue(@Param("now") LocalDateTime now, Pageable pageable);

    long countByStatusIn(Collection<String> statuses);
}
