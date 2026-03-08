package com.ming.agent12306.memory.service;

import java.util.List;

public interface MemoryVectorStore {

    void upsertSummaryVector(String sessionId, String docId, String summaryText);

    void deleteSummaryVector(String docId);

    List<String> searchRelevantSummaries(String sessionId, String query, int topK, double minScore);
}
