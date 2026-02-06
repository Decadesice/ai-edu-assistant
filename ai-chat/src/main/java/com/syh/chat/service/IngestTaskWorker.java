package com.syh.chat.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.ingest.queue", havingValue = "redis", matchIfMissing = true)
public class IngestTaskWorker {

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final StringRedisTemplate stringRedisTemplate;
    private final IngestTaskProcessor ingestTaskProcessor;
    private final String streamKey;
    private final String groupName;
    private final String consumerName;

    private Subscription subscription;

    public IngestTaskWorker(
            StreamMessageListenerContainer<String, MapRecord<String, String, String>> container,
            StringRedisTemplate stringRedisTemplate,
            IngestTaskProcessor ingestTaskProcessor,
            @Value("${app.ingest.stream-key:ingest:tasks}") String streamKey,
            @Value("${app.ingest.group:ingest-workers}") String groupName
    ) {
        this.container = container;
        this.stringRedisTemplate = stringRedisTemplate;
        this.ingestTaskProcessor = ingestTaskProcessor;
        this.streamKey = streamKey;
        this.groupName = groupName;
        this.consumerName = "c-" + UUID.randomUUID();
    }

    @PostConstruct
    public void start() {
        ensureGroup();
        this.subscription = container.receive(
                Consumer.from(groupName, consumerName),
                StreamOffset.create(streamKey, ReadOffset.lastConsumed()),
                this::handleMessage
        );
        container.start();
    }

    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.cancel();
        }
        container.stop();
    }

    private void ensureGroup() {
        Boolean exists = stringRedisTemplate.hasKey(streamKey);
        if (exists == null || !exists) {
            RecordId rid = stringRedisTemplate.opsForStream().add(streamKey, Map.of("init", "1"));
            if (rid != null) {
                stringRedisTemplate.opsForStream().delete(streamKey, rid);
            }
        }
        try {
            stringRedisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0-0"), groupName);
        } catch (Exception ignored) {
        }
    }

    private void handleMessage(MapRecord<String, String, String> record) {
        Map<String, String> value = record.getValue();
        String taskId = value.get("taskId");
        String userIdRaw = value.get("userId");
        String documentIdRaw = value.get("documentId");
        String filePath = value.get("filePath");

        if (taskId == null || userIdRaw == null || documentIdRaw == null || filePath == null) {
            acknowledge(record);
            return;
        }

        Long userId;
        Long documentId;
        try {
            userId = Long.valueOf(userIdRaw);
            documentId = Long.valueOf(documentIdRaw);
        } catch (Exception e) {
            acknowledge(record);
            return;
        }

        try {
            ingestTaskProcessor.process(taskId, userId, documentId, filePath);
        } finally {
            acknowledge(record);
        }
    }

    private void acknowledge(MapRecord<String, String, String> record) {
        try {
            stringRedisTemplate.opsForStream().acknowledge(streamKey, groupName, record.getId());
        } catch (Exception ignored) {
        }
    }
}
