package com.syh.chat.service;

import com.syh.chat.repository.OutboxEventRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "app.ingest.queue", havingValue = "kafka")
public class OutboxMetrics {

    public OutboxMetrics(OutboxEventRepository outboxEventRepository, MeterRegistry meterRegistry) {
        Gauge.builder("outbox_backlog", () -> {
                    try {
                        return (double) outboxEventRepository.countByStatusIn(List.of("NEW", "RETRYING"));
                    } catch (Exception e) {
                        return 0.0;
                    }
                })
                .register(meterRegistry);
    }
}

