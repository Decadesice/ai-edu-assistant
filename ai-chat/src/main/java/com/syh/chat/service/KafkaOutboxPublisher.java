package com.syh.chat.service;

import com.syh.chat.entity.OutboxEvent;
import com.syh.chat.repository.OutboxEventRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.ingest.queue", havingValue = "kafka")
public class KafkaOutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final int maxAttempts;

    public KafkaOutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${app.ingest.kafka.max-attempts:10}") int maxAttempts
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.ingest.kafka.publish-interval-ms:1000}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> pending = outboxEventRepository.findTop20ByStatusOrderByCreatedAtAsc("NEW");
        for (OutboxEvent evt : pending) {
            if (evt.getAttemptCount() != null && evt.getAttemptCount() >= maxAttempts) {
                evt.setStatus("DEAD");
                evt.setLastError(truncate("exceeded maxAttempts=" + maxAttempts, 2000));
                outboxEventRepository.save(evt);
                continue;
            }
            try {
                kafkaTemplate.send(evt.getTopic(), evt.getMessageKey(), evt.getPayload()).get();
                evt.setStatus("SENT");
                evt.setSentAt(LocalDateTime.now());
                outboxEventRepository.save(evt);
            } catch (Exception e) {
                evt.setAttemptCount((evt.getAttemptCount() == null ? 0 : evt.getAttemptCount()) + 1);
                evt.setLastError(truncate(String.valueOf(e.getMessage()), 2000));
                outboxEventRepository.save(evt);
            }
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }
}
