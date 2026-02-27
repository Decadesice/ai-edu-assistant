package com.syh.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syh.chat.entity.KnowledgeDocument;
import com.syh.chat.repository.KnowledgeDocumentRepository;
import com.syh.chat.repository.KnowledgeSegmentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class KnowledgeIngestServiceRedisTest {

    private KnowledgeIngestService knowledgeIngestService;

    @Mock
    private KnowledgeDocumentRepository documentRepository;

    @Mock
    private KnowledgeSegmentRepository segmentRepository;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Mock
    private RedisConnection redisConnection;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    
    private final Long userId = 1L;
    private final Long docId = 100L;
    private final String redisKey = "knowledge:summary:task:1:100";

    @BeforeEach
    void setUp() {
        // Mock Redis Template behavior
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        
        // Initialize service manually with mocks
        knowledgeIngestService = new KnowledgeIngestService(
                documentRepository,
                segmentRepository,
                null, // embeddingModel
                null, // chromaVectorStoreService
                null, // siliconFlowService
                null, // bigModelService
                "Auto",
                meterRegistry,
                redisTemplate,
                objectMapper
        );

        // Mock document repository
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setId(docId);
        doc.setUserId(userId);
        doc.setTitle("Test Doc");
        doc.setStatus("READY");
        when(documentRepository.findByIdAndUserId(eq(docId), eq(userId))).thenReturn(Optional.of(doc));
    }

    @Test
    void testGetSummaryTaskState_WithGarbageDataInRedis() {
        // 1. Simulate garbage data (invalid JSON)
        when(valueOperations.get(redisKey)).thenReturn("This is not JSON @class: garbage");

        // 2. Call the method
        Map<String, Object> state = knowledgeIngestService.getSummaryTaskState(userId, docId);

        // 3. Verify it handles it gracefully
        assertNotNull(state);
        assertEquals("IDLE", state.get("status"));
        
        // 4. Verify delete was called
        verify(redisTemplate).delete(redisKey);
    }

    @Test
    void testGetSummaryTaskState_WithValidJson() throws Exception {
        // 1. Simulate valid JSON
        Map<String, Object> data = Map.of("status", "RUNNING");
        String json = objectMapper.writeValueAsString(data);
        when(valueOperations.get(redisKey)).thenReturn(json);

        // 2. Call the method
        Map<String, Object> state = knowledgeIngestService.getSummaryTaskState(userId, docId);

        // 3. Verify
        assertEquals("RUNNING", state.get("status"));
    }
    
    @Test
    void testStartSummaryTask_WithGarbageDataInRedis() {
        // 1. Simulate setIfAbsent throwing exception (simulating what happens when existing value is weird, 
        // although StringRedisTemplate usually just overwrites, but we want to test the catch block logic 
        // which we added for extra safety)
        
        // Mock first attempt fails with exception, second attempt succeeds
        when(valueOperations.setIfAbsent(eq(redisKey), anyString(), any()))
                .thenThrow(new RuntimeException("Redis error"))
                .thenReturn(true);

        // 2. Call start task
        Map<String, Object> result = knowledgeIngestService.startSummaryTask(userId, docId);

        // 3. Verify it started successfully
        assertEquals("RUNNING", result.get("status"));

        // 4. Verify delete was called and then retry
        verify(redisTemplate).delete(redisKey);
        verify(valueOperations, times(2)).setIfAbsent(eq(redisKey), anyString(), any());
    }
}
