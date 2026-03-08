package com.ming.agent12306.memory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ming.agent12306.memory.entity.ConversationMessageEntity;
import com.ming.agent12306.memory.entity.ConversationSessionEntity;
import com.ming.agent12306.memory.mapper.ConversationMessageMapper;
import com.ming.agent12306.memory.mapper.ConversationSessionMapper;
import com.ming.agent12306.memory.model.ConversationContext;
import com.ming.agent12306.memory.model.ConversationMessage;
import com.ming.agent12306.properties.AssistantMemoryProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class AssistantMemoryService {

    private static final DateTimeFormatter MEMORY_VECTOR_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AssistantMemoryProperties memoryProperties;
    private final ConversationSessionMapper sessionMapper;
    private final ConversationMessageMapper messageMapper;
    private final ConversationSummaryGenerator summaryGenerator;
    private final MemoryVectorStore vectorStore;

    public AssistantMemoryService(
            AssistantMemoryProperties memoryProperties,
            ConversationSessionMapper sessionMapper,
            ConversationMessageMapper messageMapper,
            ConversationSummaryGenerator summaryGenerator,
            MemoryVectorStore vectorStore) {
        this.memoryProperties = memoryProperties;
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
        this.summaryGenerator = summaryGenerator;
        this.vectorStore = vectorStore;
    }

    public String ensureSessionId(String sessionId) {
        return sessionId != null && !sessionId.isBlank() ? sessionId : UUID.randomUUID().toString();
    }

    @Transactional
    public void appendMessage(String sessionId, String role, String content) {
        if (!memoryProperties.isEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        upsertSession(sessionId, now);

        ConversationMessageEntity entity = new ConversationMessageEntity();
        entity.setSessionId(sessionId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setSummarized(Boolean.FALSE);
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plusHours(memoryProperties.getTtlHours()));
        messageMapper.insert(entity);
    }

    public ConversationContext loadContext(String sessionId, String currentPrompt) {
        if (!memoryProperties.isEnabled()) {
            return new ConversationContext(sessionId, null, List.of(), List.of());
        }

        LocalDateTime now = LocalDateTime.now();
        ConversationSessionEntity session = findSession(sessionId);
        String summary = session == null || session.getExpiresAt().isBefore(now) ? null : session.getSummaryText();

        List<ConversationMessageEntity> messages = messageMapper.selectList(new LambdaQueryWrapper<ConversationMessageEntity>()
                .eq(ConversationMessageEntity::getSessionId, sessionId)
                .gt(ConversationMessageEntity::getExpiresAt, now)
                .orderByAsc(ConversationMessageEntity::getCreatedAt));

        int from = Math.max(0, messages.size() - memoryProperties.getWindowSize());
        List<ConversationMessage> recentMessages = messages.subList(from, messages.size()).stream()
                .map(item -> new ConversationMessage(item.getRole(), item.getContent(), item.getCreatedAt()))
                .toList();

        List<String> recalledMemories = vectorStore.searchRelevantSummaries(
                sessionId,
                currentPrompt,
                memoryProperties.getRecallTopK(),
                memoryProperties.getRecallScoreThreshold()
        );

        return new ConversationContext(sessionId, summary, recentMessages, recalledMemories);
    }

    @Transactional
    public void summarizeIfNecessary(String sessionId) {
        if (!memoryProperties.isEnabled()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<ConversationMessageEntity> unsummarized = messageMapper.selectList(new LambdaQueryWrapper<ConversationMessageEntity>()
                .eq(ConversationMessageEntity::getSessionId, sessionId)
                .eq(ConversationMessageEntity::getSummarized, Boolean.FALSE)
                .gt(ConversationMessageEntity::getExpiresAt, now)
                .orderByAsc(ConversationMessageEntity::getCreatedAt));

        if (unsummarized.size() <= memoryProperties.getWindowSize()) {
            return;
        }

        int summarySize = Math.min(memoryProperties.getSummaryBatchSize(), unsummarized.size() - memoryProperties.getWindowSize());
        if (summarySize <= 0) {
            return;
        }

        List<ConversationMessageEntity> batch = new ArrayList<>(unsummarized.subList(0, summarySize));
        List<String> lines = batch.stream()
                .map(item -> item.getRole() + ": " + item.getContent())
                .toList();

        ConversationSessionEntity session = upsertSession(sessionId, now);
        String mergedSummary = summaryGenerator.summarize(session.getSummaryText(), lines);
        String docId = sessionId;
        String vectorChunkId = "memory-" + sessionId + "-" + now.format(MEMORY_VECTOR_TIME_FORMATTER);

        session.setSummaryText(mergedSummary);
        session.setVectorDocId(vectorChunkId);
        session.setUpdatedAt(now);
        session.setLastActiveTime(now);
        session.setExpiresAt(now.plusHours(memoryProperties.getTtlHours()));
        sessionMapper.updateById(session);

        batch.forEach(item -> {
            item.setSummarized(Boolean.TRUE);
            messageMapper.updateById(item);
        });

        vectorStore.upsertSummaryVector(docId, vectorChunkId, mergedSummary);
    }

    public String buildPromptWithMemory(String currentPrompt, ConversationContext context) {
        if (!memoryProperties.isEnabled()) {
            return currentPrompt;
        }

        StringBuilder builder = new StringBuilder();
        if (context.summary() != null && !context.summary().isBlank()) {
            builder.append("历史会话摘要：").append(context.summary()).append("\n");
        }
        if (!context.recalledMemories().isEmpty()) {
            builder.append("相关历史记忆：\n");
            context.recalledMemories().forEach(memory ->
                    builder.append("- ").append(memory).append("\n"));
        }
        if (!context.recentMessages().isEmpty()) {
            builder.append("最近对话：\n");
            context.recentMessages().forEach(message ->
                    builder.append("- ").append(message.role()).append(": ").append(message.content()).append("\n"));
        }
        builder.append("当前用户问题：").append(currentPrompt);
        return builder.toString();
    }

    public String buildPreprocessInput(String currentPrompt, ConversationContext context) {
        if (!memoryProperties.isEnabled()) {
            return currentPrompt;
        }

        StringBuilder builder = new StringBuilder();
        if (context.summary() != null && !context.summary().isBlank()) {
            builder.append("历史会话摘要：").append(context.summary()).append("\n");
        }
        if (!context.recalledMemories().isEmpty()) {
            builder.append("召回到的相关历史记忆：\n");
            context.recalledMemories().forEach(memory ->
                    builder.append("- ").append(memory).append("\n"));
        }
        if (!context.recentMessages().isEmpty()) {
            builder.append("最近对话：\n");
            context.recentMessages().forEach(message ->
                    builder.append("- ").append(message.role()).append(": ").append(message.content()).append("\n"));
        }
        builder.append("当前用户新问题：").append(currentPrompt);
        return builder.toString();
    }

    private ConversationSessionEntity upsertSession(String sessionId, LocalDateTime now) {
        ConversationSessionEntity session = findSession(sessionId);
        if (session == null) {
            session = new ConversationSessionEntity();
            session.setSessionId(sessionId);
            session.setCreatedAt(now);
        }
        session.setLastActiveTime(now);
        session.setUpdatedAt(now);
        session.setExpiresAt(now.plusHours(memoryProperties.getTtlHours()));
        if (session.getId() == null) {
            sessionMapper.insert(session);
        } else {
            sessionMapper.updateById(session);
        }
        return session;
    }

    private ConversationSessionEntity findSession(String sessionId) {
        return sessionMapper.selectOne(new LambdaQueryWrapper<ConversationSessionEntity>()
                .eq(ConversationSessionEntity::getSessionId, sessionId)
                .last("limit 1"));
    }
}
