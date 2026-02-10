package com.syh.chat.service;

import com.syh.chat.entity.OutboxEvent;
import com.syh.chat.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Component
@ConditionalOnProperty(name = "app.ingest.queue", havingValue = "kafka")
public class KafkaOutboxPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;
    private final int maxAttempts;
    private final Duration baseBackoff;
    private final Duration maxBackoff;
    private final int batchSize;

    public KafkaOutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.ingest.kafka.max-attempts:10}") int maxAttempts,
            @Value("${app.ingest.kafka.retry.base-backoff-ms:1000}") long baseBackoffMs,
            @Value("${app.ingest.kafka.retry.max-backoff-ms:600000}") long maxBackoffMs,
            @Value("${app.ingest.kafka.publish-batch-size:20}") int batchSize
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
        this.maxAttempts = maxAttempts;
        this.baseBackoff = Duration.ofMillis(Math.max(0, baseBackoffMs));
        this.maxBackoff = Duration.ofMillis(Math.max(0, maxBackoffMs));
        this.batchSize = Math.max(1, batchSize);
    }

    @Scheduled(fixedDelayString = "${app.ingest.kafka.publish-interval-ms:1000}")
    public void publishPending() {
        LocalDateTime now = LocalDateTime.now();
        List<OutboxEvent> due = outboxEventRepository.findDue(now, PageRequest.of(0, batchSize));
        for (OutboxEvent evt : due) {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                if (evt.getAttemptCount() != null && evt.getAttemptCount() >= maxAttempts) {
                    markDead(evt, now, "exceeded maxAttempts=" + maxAttempts);
                    Counter.builder("outbox_publish_total").tag("result", "dead").register(meterRegistry).increment();
                    sample.stop(Timer.builder("outbox_publish_seconds").tag("result", "dead").register(meterRegistry));
                    continue;
                }
                String topic = evt.getTopic();
                String key = evt.getMessageKey();
                String payload = evt.getPayload();
                if (topic == null || key == null || payload == null) {
                    throw new IllegalStateException("outbox event missing topic/key/payload");
                }
                kafkaTemplate.send(Objects.requireNonNull(topic), Objects.requireNonNull(key), Objects.requireNonNull(payload)).get();
                evt.setStatus("SENT");
                evt.setSentAt(LocalDateTime.now());
                evt.setNextRetryAt(null);
                evt.setLastError(null);
                outboxEventRepository.save(evt);
                Counter.builder("outbox_publish_total").tag("result", "success").register(meterRegistry).increment();
                recordDelay(evt);
                sample.stop(Timer.builder("outbox_publish_seconds").tag("result", "success").register(meterRegistry));
            } catch (Exception e) {
                int currentAttempt = evt.getAttemptCount() == null ? 0 : evt.getAttemptCount();
                int nextAttempt = currentAttempt + 1;
                evt.setAttemptCount(nextAttempt);
                evt.setLastError(truncate(String.valueOf(e.getMessage()), 2000));

                if (nextAttempt >= maxAttempts) {
                    markDead(evt, now, evt.getLastError());
                    Counter.builder("outbox_publish_total").tag("result", "dead").register(meterRegistry).increment();
                    sample.stop(Timer.builder("outbox_publish_seconds").tag("result", "dead").register(meterRegistry));
                } else {
                    evt.setStatus("RETRYING");
                    evt.setNextRetryAt(now.plus(computeBackoff(nextAttempt)));
                    outboxEventRepository.save(evt);
                    Counter.builder("outbox_publish_total").tag("result", "failure").register(meterRegistry).increment();
                    sample.stop(Timer.builder("outbox_publish_seconds").tag("result", "failure").register(meterRegistry));
                }
            }
        }
    }

    private void markDead(OutboxEvent evt, LocalDateTime now, String reason) {
        evt.setStatus("DEAD");
        evt.setNextRetryAt(null);
        evt.setLastError(truncate(reason, 2000));
        outboxEventRepository.save(evt);
    }

    private void recordDelay(OutboxEvent evt) {
        if (evt.getCreatedAt() == null || evt.getSentAt() == null) {
            return;
        }
        Duration delay = Duration.between(evt.getCreatedAt(), evt.getSentAt());
        if (delay.isNegative()) {
            return;
        }
        Timer.builder("outbox_publish_delay_seconds").register(meterRegistry).record(delay);
    }

    private Duration computeBackoff(int attempt) {
        if (attempt <= 0) {
            return Duration.ZERO;
        }
        long multiplier;
        if (attempt >= 31) {
            multiplier = Long.MAX_VALUE;
        } else {
            multiplier = 1L << (attempt - 1);
        }
        long millis;
        try {
            millis = Math.multiplyExact(baseBackoff.toMillis(), multiplier);
        } catch (ArithmeticException e) {
            millis = Long.MAX_VALUE;
        }
        millis = Math.min(millis, maxBackoff.toMillis());
        if (millis < 0) {
            millis = maxBackoff.toMillis();
        }
        return Duration.ofMillis(millis);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen);
    }
}
