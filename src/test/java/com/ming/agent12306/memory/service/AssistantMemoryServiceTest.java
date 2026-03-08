package com.ming.agent12306.memory.service;

import com.ming.agent12306.memory.entity.ConversationMessageEntity;
import com.ming.agent12306.memory.entity.ConversationSessionEntity;
import com.ming.agent12306.memory.mapper.ConversationMessageMapper;
import com.ming.agent12306.memory.mapper.ConversationSessionMapper;
import com.ming.agent12306.memory.model.ConversationContext;
import com.ming.agent12306.properties.AssistantMemoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssistantMemoryServiceTest {

    @Mock
    private ConversationSessionMapper sessionMapper;

    @Mock
    private ConversationMessageMapper messageMapper;

    @Mock
    private ConversationSummaryGenerator summaryGenerator;

    @Mock
    private MemoryVectorStore vectorStore;

    private AssistantMemoryService assistantMemoryService;

    @BeforeEach
    void setUp() {
        AssistantMemoryProperties properties = new AssistantMemoryProperties();
        properties.setEnabled(true);
        properties.setWindowSize(3);
        properties.setSummaryBatchSize(2);
        properties.setTtlHours(24);

        assistantMemoryService = new AssistantMemoryService(
                properties,
                sessionMapper,
                messageMapper,
                summaryGenerator,
                vectorStore
        );
    }

    @Test
    void shouldLoadOnlyRecentWindowMessages() {
        LocalDateTime now = LocalDateTime.now();
        ConversationSessionEntity session = new ConversationSessionEntity();
        session.setSessionId("s1");
        session.setSummaryText("历史摘要");
        session.setExpiresAt(now.plusHours(1));

        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message("user", "m1", now.minusMinutes(5)),
                message("assistant", "m2", now.minusMinutes(4)),
                message("user", "m3", now.minusMinutes(3)),
                message("assistant", "m4", now.minusMinutes(2)),
                message("user", "m5", now.minusMinutes(1))
        ));
        when(vectorStore.searchRelevantSummaries("s1", "查一下北京到上海", 3, 0.6D))
                .thenReturn(List.of("用户之前偏好高铁二等座"));

        ConversationContext context = assistantMemoryService.loadContext("s1", "查一下北京到上海");

        assertEquals("s1", context.sessionId());
        assertEquals("历史摘要", context.summary());
        assertEquals(3, context.recentMessages().size());
        assertEquals("m3", context.recentMessages().get(0).content());
        assertEquals("m5", context.recentMessages().get(2).content());
        assertEquals(1, context.recalledMemories().size());
        assertEquals("用户之前偏好高铁二等座", context.recalledMemories().get(0));

        String prompt = assistantMemoryService.buildPromptWithMemory("查一下北京到上海", context);
        assertTrue(prompt.contains("相关历史记忆"));
        assertTrue(prompt.contains("用户之前偏好高铁二等座"));
    }

    @Test
    void shouldSummarizeOldMessagesWhenWindowExceeded() {
        LocalDateTime now = LocalDateTime.now();
        ConversationSessionEntity session = new ConversationSessionEntity();
        session.setId(1L);
        session.setSessionId("s1");
        session.setSummaryText("旧摘要");
        session.setExpiresAt(now.plusHours(1));

        List<ConversationMessageEntity> unsummarized = List.of(
                message("user", "u1", now.minusMinutes(5)),
                message("assistant", "a1", now.minusMinutes(4)),
                message("user", "u2", now.minusMinutes(3)),
                message("assistant", "a2", now.minusMinutes(2)),
                message("user", "u3", now.minusMinutes(1))
        );

        when(sessionMapper.selectOne(any())).thenReturn(session);
        when(messageMapper.selectList(any())).thenReturn(unsummarized);
        when(summaryGenerator.summarize(eq("旧摘要"), eq(List.of("user: u1", "assistant: a1"))))
                .thenReturn("新摘要");

        assistantMemoryService.summarizeIfNecessary("s1");

        ArgumentCaptor<ConversationSessionEntity> sessionCaptor = ArgumentCaptor.forClass(ConversationSessionEntity.class);
        verify(sessionMapper, times(2)).updateById(sessionCaptor.capture());
        ConversationSessionEntity updated = sessionCaptor.getAllValues().get(1);

        assertEquals("新摘要", updated.getSummaryText());
        assertTrue(updated.getVectorDocId().startsWith("memory-s1-"));
        assertNotNull(updated.getUpdatedAt());

        ArgumentCaptor<ConversationMessageEntity> messageCaptor = ArgumentCaptor.forClass(ConversationMessageEntity.class);
        verify(messageMapper, times(2)).updateById(messageCaptor.capture());
        assertEquals(2, messageCaptor.getAllValues().size());
        assertEquals(Boolean.TRUE, messageCaptor.getAllValues().get(0).getSummarized());
        assertEquals(Boolean.TRUE, messageCaptor.getAllValues().get(1).getSummarized());

        verify(vectorStore).upsertSummaryVector(eq("s1"), any(), eq("新摘要"));
    }

    private ConversationMessageEntity message(String role, String content, LocalDateTime createdAt) {
        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setRole(role);
        entity.setContent(content);
        entity.setCreatedAt(createdAt);
        entity.setExpiresAt(createdAt.plusHours(24));
        entity.setSummarized(Boolean.FALSE);
        return entity;
    }
}
