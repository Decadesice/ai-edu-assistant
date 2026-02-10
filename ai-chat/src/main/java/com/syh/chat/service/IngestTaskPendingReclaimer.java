package com.syh.chat.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.ingest.queue", havingValue = "redis", matchIfMissing = true)
@ConditionalOnProperty(name = "app.ingest.redis.reclaim-enabled", havingValue = "true", matchIfMissing = true)
public class IngestTaskPendingReclaimer {

    private final StringRedisTemplate stringRedisTemplate;
    private final IngestTaskProcessor ingestTaskProcessor;
    private final MeterRegistry meterRegistry;
    private final String streamKey;
    private final String groupName;
    private final String consumerName;
    private final long minIdleMs;
    private final int batchSize;
    private final String dlqStreamKey;

    public IngestTaskPendingReclaimer(
            StringRedisTemplate stringRedisTemplate,
            IngestTaskProcessor ingestTaskProcessor,
            MeterRegistry meterRegistry,
            @Value("${app.ingest.stream-key:ingest:tasks}") String streamKey,
            @Value("${app.ingest.group:ingest-workers}") String groupName,
            @Value("${app.ingest.redis.reclaim-idle-ms:600000}") long minIdleMs,
            @Value("${app.ingest.redis.reclaim-batch-size:20}") int batchSize,
            @Value("${app.ingest.redis.dlq-stream-key:ingest:tasks:dlq}") String dlqStreamKey
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.ingestTaskProcessor = ingestTaskProcessor;
        this.meterRegistry = meterRegistry;
        this.streamKey = streamKey;
        this.groupName = groupName;
        this.consumerName = "reclaimer-" + UUID.randomUUID();
        this.minIdleMs = Math.max(0, minIdleMs);
        this.batchSize = Math.max(1, batchSize);
        this.dlqStreamKey = dlqStreamKey;
    }

    @Scheduled(fixedDelayString = "${app.ingest.redis.reclaim-interval-ms:5000}")
    public void reclaimOnce() {
        StreamOperations<String, String, String> ops = stringRedisTemplate.opsForStream();
        PendingMessages pending;
        try {
            pending = ops.pending(
                    Objects.requireNonNull(streamKey),
                    Objects.requireNonNull(groupName),
                    org.springframework.data.domain.Range.unbounded(),
                    (long) batchSize
            );
        } catch (Exception e) {
            return;
        }

        if (pending == null || pending.isEmpty()) {
            return;
        }

        List<RecordId> ids = new ArrayList<>();
        for (PendingMessage p : pending) {
            if (p == null) {
                continue;
            }
            if (p.getElapsedTimeSinceLastDelivery() != null && p.getElapsedTimeSinceLastDelivery().toMillis() >= minIdleMs) {
                ids.add(p.getId());
            }
        }
        if (ids.isEmpty()) {
            return;
        }

        List<MapRecord<String, String, String>> claimed;
        try {
            claimed = ops.claim(
                    Objects.requireNonNull(streamKey),
                    Objects.requireNonNull(groupName),
                    Objects.requireNonNull(consumerName),
                    Objects.requireNonNull(Duration.ofMillis(minIdleMs)),
                    Objects.requireNonNull(ids.toArray(new RecordId[0]))
            );
        } catch (Exception e) {
            return;
        }

        if (claimed == null || claimed.isEmpty()) {
            return;
        }

        Counter.builder("ingest_stream_reclaim_total").tag("result", "claimed").register(meterRegistry).increment(claimed.size());

        for (MapRecord<String, String, String> record : claimed) {
            Map<String, String> value = record.getValue();
            String taskId = value.get("taskId");
            String userIdRaw = value.get("userId");
            String documentIdRaw = value.get("documentId");
            String filePath = value.get("filePath");

            if (taskId == null || userIdRaw == null || documentIdRaw == null || filePath == null) {
                acknowledge(record);
                continue;
            }

            Long userId;
            Long documentId;
            try {
                userId = Long.valueOf(userIdRaw);
                documentId = Long.valueOf(documentIdRaw);
            } catch (Exception e) {
                acknowledge(record);
                continue;
            }

            IngestTaskProcessingResult result;
            try {
                result = ingestTaskProcessor.process(taskId, userId, documentId, filePath);
            } catch (Exception e) {
                Counter.builder("ingest_stream_reclaim_total").tag("result", "error").register(meterRegistry).increment();
                continue;
            }

            if (result != null && result.shouldAck()) {
                if (result == IngestTaskProcessingResult.DEAD) {
                    writeDlq(record, taskId);
                    Counter.builder("ingest_task_dlq_total").register(meterRegistry).increment();
                }
                acknowledge(record);
            }
        }
    }

    private void writeDlq(MapRecord<String, String, String> record, String taskId) {
        if (dlqStreamKey == null || dlqStreamKey.isBlank()) {
            return;
        }
        try {
            String recordId = record.getId() == null ? "" : String.valueOf(record.getId().getValue());
            Map<String, String> payload = new HashMap<>();
            payload.put("taskId", String.valueOf(taskId));
            payload.put("sourceStream", String.valueOf(streamKey));
            payload.put("sourceGroup", String.valueOf(groupName));
            payload.put("sourceRecordId", recordId);
            stringRedisTemplate.opsForStream().add(Objects.requireNonNull(dlqStreamKey), payload);
        } catch (Exception ignored) {
        }
    }

    private void acknowledge(MapRecord<String, String, String> record) {
        try {
            stringRedisTemplate.opsForStream().acknowledge(
                    Objects.requireNonNull(streamKey),
                    Objects.requireNonNull(groupName),
                    record.getId()
            );
        } catch (Exception ignored) {
        }
    }
}
