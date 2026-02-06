package com.syh.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syh.chat.dto.IngestTaskEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.ingest.queue", havingValue = "kafka")
public class IngestTaskKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final IngestTaskProcessor ingestTaskProcessor;

    public IngestTaskKafkaConsumer(
            ObjectMapper objectMapper,
            IngestTaskProcessor ingestTaskProcessor
    ) {
        this.objectMapper = objectMapper;
        this.ingestTaskProcessor = ingestTaskProcessor;
    }

    @KafkaListener(
            topics = "${app.ingest.kafka.topic:ingest-tasks}",
            groupId = "${app.ingest.kafka.group:ingest-workers}"
    )
    public void onMessage(String payload, Acknowledgment ack) {
        try {
            IngestTaskEvent evt = objectMapper.readValue(payload, IngestTaskEvent.class);
            ingestTaskProcessor.process(evt.getTaskId(), evt.getUserId(), evt.getDocumentId(), evt.getFilePath());
            ack.acknowledge();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
