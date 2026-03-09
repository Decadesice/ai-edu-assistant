package com.syh.chat.service;

import com.syh.chat.entity.OutboxEvent;
import com.syh.chat.repository.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("null")
public class KafkaOutboxPublisherTest {

    @Test
    void publishFailureMovesToRetryingWithNextRetryAt() {
        OutboxEventRepository repo = mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        OutboxEvent evt = new OutboxEvent();
        evt.setId("1");
        evt.setTopic("t");
        evt.setMessageKey("k");
        evt.setPayload("{}");
        evt.setStatus("NEW");
        evt.setAttemptCount(0);
        evt.setCreatedAt(LocalDateTime.now().minusSeconds(5));
        evt.setNextRetryAt(null);

        when(repo.findDue(any(), any(Pageable.class))).thenReturn(List.of(evt));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        KafkaOutboxPublisher publisher = new KafkaOutboxPublisher(repo, kafkaTemplate, meterRegistry, 3, 1000, 600000, 20);
        publisher.publishPending();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repo, atLeastOnce()).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertEquals("RETRYING", saved.getStatus());
        assertEquals(1, saved.getAttemptCount());
        assertNotNull(saved.getNextRetryAt());
        assertTrue(saved.getLastError() != null && saved.getLastError().contains("kafka down"));
    }

    @Test
    void publishFailureExceedingMaxAttemptsMovesToDead() {
        OutboxEventRepository repo = mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        OutboxEvent evt = new OutboxEvent();
        evt.setId("1");
        evt.setTopic("t");
        evt.setMessageKey("k");
        evt.setPayload("{}");
        evt.setStatus("RETRYING");
        evt.setAttemptCount(2);
        evt.setCreatedAt(LocalDateTime.now().minusSeconds(5));
        evt.setNextRetryAt(LocalDateTime.now().minusSeconds(1));

        when(repo.findDue(any(), any(Pageable.class))).thenReturn(List.of(evt));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        KafkaOutboxPublisher publisher = new KafkaOutboxPublisher(repo, kafkaTemplate, meterRegistry, 3, 1000, 600000, 20);
        publisher.publishPending();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repo, atLeastOnce()).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertEquals("DEAD", saved.getStatus());
        assertEquals(3, saved.getAttemptCount());
        assertNull(saved.getNextRetryAt());
    }

    @Test
    void publishSuccessMarksSentAndRecordsSentAt() {
        OutboxEventRepository repo = mock(OutboxEventRepository.class);
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafkaTemplate = (KafkaTemplate<String, String>) mock(KafkaTemplate.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

        OutboxEvent evt = new OutboxEvent();
        evt.setId("1");
        evt.setTopic("t");
        evt.setMessageKey("k");
        evt.setPayload("{}");
        evt.setStatus("NEW");
        evt.setAttemptCount(0);
        evt.setCreatedAt(LocalDateTime.now().minusSeconds(5));
        evt.setNextRetryAt(null);
        evt.setLastError("x");

        when(repo.findDue(any(), any(Pageable.class))).thenReturn(List.of(evt));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        KafkaOutboxPublisher publisher = new KafkaOutboxPublisher(repo, kafkaTemplate, meterRegistry, 3, 1000, 600000, 20);
        publisher.publishPending();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repo, atLeastOnce()).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertEquals("SENT", saved.getStatus());
        assertNotNull(saved.getSentAt());
        assertNull(saved.getNextRetryAt());
        assertNull(saved.getLastError());
    }
}
