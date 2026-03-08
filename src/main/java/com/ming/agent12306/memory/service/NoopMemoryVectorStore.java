package com.ming.agent12306.memory.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "assistant.memory.milvus", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoopMemoryVectorStore implements MemoryVectorStore {

    @Override
    public void upsertSummaryVector(String sessionId, String docId, String summaryText) {
    }

    @Override
    public void deleteSummaryVector(String docId) {
    }

    @Override
    public List<String> searchRelevantSummaries(String sessionId, String query, int topK, double minScore) {
        return List.of();
    }
}
