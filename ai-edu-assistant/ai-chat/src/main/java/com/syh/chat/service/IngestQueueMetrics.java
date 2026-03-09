package com.syh.chat.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(name = "app.ingest.queue", havingValue = "redis", matchIfMissing = true)
public class IngestQueueMetrics {

    public IngestQueueMetrics(
            StringRedisTemplate stringRedisTemplate,
            MeterRegistry meterRegistry,
            @Value("${app.ingest.stream-key:ingest:tasks}") String streamKey,
            @Value("${app.ingest.group:ingest-workers}") String groupName
    ) {
        Gauge.builder("ingest_stream_length", () -> {
                    try {
                        Long size = stringRedisTemplate.opsForStream().size(Objects.requireNonNull(streamKey));
                        return size == null ? 0.0 : size.doubleValue();
                    } catch (Exception e) {
                        return 0.0;
                    }
                })
                .tag("stream", Objects.requireNonNull(streamKey))
                .register(meterRegistry);

        Gauge.builder("ingest_stream_pending", () -> {
                    try {
                        PendingMessagesSummary pending = stringRedisTemplate.opsForStream().pending(
                                Objects.requireNonNull(streamKey),
                                Objects.requireNonNull(groupName)
                        );
                        if (pending == null) {
                            return 0.0;
                        }
                        return (double) pending.getTotalPendingMessages();
                    } catch (Exception e) {
                        return 0.0;
                    }
                })
                .tag("stream", Objects.requireNonNull(streamKey))
                .tag("group", Objects.requireNonNull(groupName))
                .register(meterRegistry);
    }
}
