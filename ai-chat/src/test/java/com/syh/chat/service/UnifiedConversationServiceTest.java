package com.syh.chat.service;

import com.syh.chat.dto.ConversationSummaryResponse;
import com.syh.chat.entity.Conversation;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UnifiedConversationServiceTest {

    @Test
    void getUserConversationSummaries_shouldReturnTitleFallbackAndUpdatedAt() {
        RedisConversationService redisConversationService = mock(RedisConversationService.class);
        DatabaseConversationService databaseConversationService = mock(DatabaseConversationService.class);
        BigModelService bigModelService = mock(BigModelService.class);
        SiliconFlowService siliconFlowService = mock(SiliconFlowService.class);

        UnifiedConversationService service = new UnifiedConversationService(
                redisConversationService,
                databaseConversationService,
                bigModelService,
                siliconFlowService
        );

        Conversation c1 = new Conversation();
        c1.setSessionId("s1");
        c1.setTitle("");
        c1.setModelName("qwen3-vl:2b");
        c1.setCreatedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        c1.setUpdatedAt(null);

        when(databaseConversationService.getUserConversations(1L)).thenReturn(List.of(c1));

        List<ConversationSummaryResponse> result = service.getUserConversationSummaries(1L);
        assertEquals(1, result.size());
        assertEquals("s1", result.get(0).getSessionId());
        assertEquals("新对话", result.get(0).getTitle());
        assertEquals("qwen3-vl:2b", result.get(0).getModelName());
        assertEquals(c1.getCreatedAt(), result.get(0).getUpdatedAt());
    }
}


